#!/usr/bin/env bash
# Build, checksum, and publish a self-update GitHub Release for the native
# OpenBubbles Android client.
#
# Run on a machine that holds the production keystore (android/key.properties);
# CI never sees that key, so CI cannot publish production-signed updates.
#
# Each release carries two assets:
#   openbubbles-<versionName>-<versionCode>.apk — universal release APK
#   update.json                    — the in-app update feed consumed by
#                                    app.openbubbles.nativeapp.update
#
# Usage:
#   scripts/publish-update.sh --set --version-name 2.0.1 --version-code 20002237 \
#       [--notes-file notes.md | --notes "text"] [--dry-run] [--skip-build]
#
# --set          rewrite versionName/versionCode in app-native/build.gradle
# --dry-run      do everything except create the GitHub release
# --skip-build   reuse the APK already built at the expected output path
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_PROJECT="$REPO_ROOT/app-native"
BUILD_GRADLE="$GRADLE_PROJECT/build.gradle"

die() { echo "publish-update: $*" >&2; exit 1; }
log()  { echo "publish-update: $*"; }

# Portable in-place sed (BSD vs GNU).
sed_i() {
    if sed --version >/dev/null 2>&1; then sed -i "$@"; else sed -i '' "$@"; fi
}

# ---------------------------------------------------------------- args -----
VERSION_NAME=""
VERSION_CODE=""
SET_VERSION=0
DRY_RUN=0
SKIP_BUILD=0
NOTES=""
NOTES_FILE=""
REPO_SLUG=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version-name) VERSION_NAME="${2:?--version-name needs a value}"; shift 2 ;;
        --version-code) VERSION_CODE="${2:?--version-code needs a value}"; shift 2 ;;
        --set) SET_VERSION=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --skip-build) SKIP_BUILD=1; shift ;;
        --notes) NOTES="${2:?--notes needs a value}"; shift 2 ;;
        --notes-file) NOTES_FILE="${2:?--notes-file needs a path}"; shift 2 ;;
        --repo) REPO_SLUG="${2:?--repo needs owner/name}"; shift 2 ;;
        -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

[ -n "$VERSION_NAME" ] || die "missing --version-name (e.g. 2.0.1)"
[ -n "$VERSION_CODE" ] || die "missing --version-code (must be an integer > previous release)"
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || die "--version-code must be numeric, got: $VERSION_CODE"
[[ "$VERSION_NAME" =~ ^[0-9]+(\.[0-9]+)*$ ]] || die "--version-name must look like 2.0.1, got: $VERSION_NAME"

# --------------------------------------------------------------- notes -----
# Resolution order: --notes/--notes-file, then the matching `## v<version>`
# section of assets/changelog/changelog.md, then the commit log.
if [ -n "$NOTES_FILE" ] && [ -n "$NOTES" ]; then
    die "pass either --notes or --notes-file, not both"
fi
if [ -n "$NOTES_FILE" ]; then
    [ -f "$NOTES_FILE" ] || die "notes file not found: $NOTES_FILE"
    NOTES="$(cat "$NOTES_FILE")"
fi
if [ -z "$NOTES" ] && [ -f "$REPO_ROOT/assets/changelog/changelog.md" ]; then
    NOTES="$("$REPO_ROOT/scripts/extract-release-notes.py" "$VERSION_NAME" \
        "$REPO_ROOT/assets/changelog/changelog.md")"
fi
if [ -z "$NOTES" ]; then
    PREV_TAG="$(git -C "$REPO_ROOT" describe --tags --abbrev=0 2>/dev/null || true)"
    if [ -n "$PREV_TAG" ]; then
        NOTES="$(git -C "$REPO_ROOT" log --oneline "$PREV_TAG..HEAD")"
    else
        NOTES="$(git -C "$REPO_ROOT" log --oneline -20)"
    fi
fi
[ "$NOTES" != "$(printf '%s' "$NOTES" | head -c 4000)" ] && \
    log "warning: notes longer than 4000 chars will be trimmed"

# ------------------------------------------------------------ tooling ------
command -v gh >/dev/null 2>&1 || die "gh CLI not installed (https://cli.github.com)"
command -v python3 >/dev/null 2>&1 || die "python3 required to build update.json"
gh auth status >/dev/null 2>&1 || die "gh not authenticated: run 'gh auth login'"

if [ -z "$REPO_SLUG" ]; then
    remote="$(git -C "$REPO_ROOT" remote get-url origin)"
    REPO_SLUG="$(basename "${remote%%.git}")"
    owner="$(basename "$(dirname "${remote%%.git}")")"
    case "$remote" in
        *github.com*) REPO_SLUG="$owner/$REPO_SLUG" ;;
        *) die "origin is not a GitHub remote; pass --repo owner/name" ;;
    esac
fi
log "target repo: $REPO_SLUG"

# ------------------------------------------------------------ keystore -----
KEY_PROPERTIES="$REPO_ROOT/android/key.properties"
[ -f "$KEY_PROPERTIES" ] || die "android/key.properties missing — updates must be production-signed"
store_file="$(sed -n 's/^storeFile[ :=]*\(.*\)$/\1/p' "$KEY_PROPERTIES" | tr -d ' "')"
[ -n "$store_file" ] || die "key.properties has no storeFile"
if [ ! -f "$store_file" ] && [ ! -f "$REPO_ROOT/android/$store_file" ]; then
    die "keystore not found at: $store_file"
fi
log "production keystore present"

# ------------------------------------------------------- version gate ------
read_gradle() {
    awk -v key="$1" '$1 == key { $1 = ""; sub(/^[[:space:]]+/, ""); print; exit }' "$BUILD_GRADLE"
}
current_name="$(read_gradle versionName | tr -d '"')"
current_code="$(read_gradle versionCode)"
[ -n "$current_name" ] && [ -n "$current_code" ] || \
    die "cannot read versionName/versionCode from app-native/build.gradle"

if [ "$SET_VERSION" -eq 1 ]; then
    sed_i "s/^[[:space:]]*versionName .*/        versionName \"$VERSION_NAME\"/" "$BUILD_GRADLE"
    sed_i "s/^[[:space:]]*versionCode .*/        versionCode $VERSION_CODE/" "$BUILD_GRADLE"
    log "build.gradle: $current_name ($current_code) -> $VERSION_NAME ($VERSION_CODE)"
elif [ "$current_name" != "$VERSION_NAME" ] || [ "$current_code" != "$VERSION_CODE" ]; then
    die "build.gradle has $current_name ($current_code) but you asked to publish \
$VERSION_NAME ($VERSION_CODE). Bump it (or pass --set)."
fi

# Never publish a versionCode the in-app updater would refuse to install.
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
if gh api "repos/$REPO_SLUG/releases/latest" --jq '.tag_name' >/dev/null 2>&1; then
    asset_url="$(gh api "repos/$REPO_SLUG/releases/latest" \
        --jq '.assets[] | select(.name == "update.json") | .url' || true)"
    if [ -n "$asset_url" ]; then
        gh api "$asset_url" -H "Accept: application/octet-stream" > "$TMP_DIR/prev-update.json"
        prev_code="$(python3 -c \
            "import json;print(json.load(open('$TMP_DIR/prev-update.json'))['versionCode'])")"
        if [ "$VERSION_CODE" -le "$prev_code" ]; then
            die "refusing downgrade: previous release versionCode=$prev_code, publishing $VERSION_CODE"
        fi
        log "monotonicity ok: $prev_code -> $VERSION_CODE"
    else
        log "latest release has no update.json asset (first self-update release?)"
    fi
else
    log "no existing releases — first publish"
fi

# --------------------------------------------------------------- build -----
APK_PATH="$REPO_ROOT/build-native/app-native/outputs/apk/release/app-native-release.apk"
if [ "$SKIP_BUILD" -eq 1 ]; then
    [ -f "$APK_PATH" ] || die "--skip-build but no APK at $APK_PATH"
else
    log "building release APK (JDK 21 required)…"
    (cd "$REPO_ROOT/native" && ./gradlew :app-native:assembleRelease --console=plain)
fi
[ -f "$APK_PATH" ] || die "build finished but APK not found at $APK_PATH (check output path)"

# ----------------------------------------------------------- checksum ------
SHA256="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
BYTES="$(wc -c < "$APK_PATH" | tr -d ' ')"
APK_ASSET="openbubbles-$VERSION_NAME-$VERSION_CODE.apk"
log "apk: $APK_ASSET ($BYTES bytes, sha256 $SHA256)"

STAGED_APK="$TMP_DIR/$APK_ASSET"
cp "$APK_PATH" "$STAGED_APK"

# ----------------------------------------------------------- feed ----------
python3 - "$STAGED_APK" "$VERSION_CODE" "$VERSION_NAME" "$SHA256" "$BYTES" "$APK_ASSET" "$NOTES" <<'PY' > "$TMP_DIR/update.json"
import json, sys
apk, code, name, sha, size, asset, notes = sys.argv[1:8]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "apkAsset": asset,
    "sha256": sha,
    "bytes": int(size),
    "notes": notes[:4000],
    "minVersionCode": 0,
}, sys.stdout, indent=2)
print()
PY
cat "$TMP_DIR/update.json"
printf '%s' "$NOTES" > "$TMP_DIR/release-notes.md"

# ------------------------------------------------------------ publish ------
TAG="v$VERSION_NAME-$VERSION_CODE"
TARGET_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
if [ "$DRY_RUN" -eq 1 ]; then
    log "dry-run: would run: gh release create $TAG $APK_ASSET update.json (repo $REPO_SLUG, target $TARGET_SHA)"
    exit 0
fi
if gh api "repos/$REPO_SLUG/releases/tags/$TAG" >/dev/null 2>&1; then
    die "tag $TAG already exists; version names must be unique"
fi
(cd "$TMP_DIR" && gh release create "$TAG" \
    "$APK_ASSET" "update.json" \
    --repo "$REPO_SLUG" \
    --target "$TARGET_SHA" \
    --title "OpenBubbles $VERSION_NAME" \
    --notes-file release-notes.md)
log "published $TAG to $REPO_SLUG — devices will pick it up on the next check"
