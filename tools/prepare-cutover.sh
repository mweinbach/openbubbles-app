#!/bin/bash
# M4 cutover preparation — run from the repo root on a fresh m4-cutover
# branch (see tools/CUTOVER.md). Each phase is a separate commit.
# ARCHIVED: this records the original cutover and is not current build guidance.
# Its Cargokit references predate the direct Cargo/NDK Android build in
# app-native/cargo-android.gradle. Do not reintroduce Dart or Cargokit from here.
#
#   git checkout -b m4-cutover main
#   bash tools/prepare-cutover.sh
#
# Phases:
#   1. identity  — applicationId/versionCode continuity + release signing
#   2. relocate  — uniffi bindings + db seed model out of android/ and lib/
#   3. delete    — remove the Flutter app (lib/, android/, windows/, pubspec,
#                  rust_builder platform dirs; cargokit is KEPT)
#   4. ci        — drop build.yml, native.yml stands alone
set -euo pipefail

confirm() { read -r -p "phase: $1 — proceed? [y/N] " a; [[ "$a" == "y" ]] || exit 1; }

# ---------------------------------------------------------------- phase 1
confirm "identity (applicationId com.openbubbles.messaging, versionCode 20002236)"
python3 - <<'EOF'
p = 'app-native/build.gradle'
s = open(p).read()
s = s.replace('applicationId "com.openbubbles.messaging.native"', 'applicationId "com.openbubbles.messaging"')
s = s.replace('versionCode 1', 'versionCode 20002236')   # continue the Flutter prod scheme
s = s.replace('versionName "0.1.0"', 'versionName "2.0.0"')
# release signing reuses the Flutter keystore when key.properties exists
s = s.replace('''    buildTypes {
        release {
            // Hello-stage signing; the real keystore wiring lands with M4.
            signingConfig signingConfigs.debug
            minifyEnabled false
        }
    }''', '''    def keystoreProperties = new Properties()
    def keystorePropertiesFile = rootProject.file("../android/key.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.withReader('UTF-8') { keystoreProperties.load(it) }
    }
    signingConfigs {
        release {
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile keystoreProperties['storeFile'] ? file(keystoreProperties['storeFile']) : null
            storePassword keystoreProperties['storePassword']
        }
    }
    buildTypes {
        release {
            signingConfig keystorePropertiesFile.exists() ? signingConfigs.release : signingConfigs.debug
            minifyEnabled false
        }
    }''')
open(p, 'w').write(s)
print('identity updated')
EOF
git add -A && git commit -m "cutover: applicationId + version continuity + release signing"

# ---------------------------------------------------------------- phase 2
confirm "relocate (bindings -> core, db seed model)"
git mv android/app/src/main/kotlin/uniffi core/src/main/kotlin/uniffi
mkdir -p core/src/test/kotlin && cp -r core/src/main/kotlin/uniffi core/src/test/kotlin/uniffi 2>/dev/null || true
python3 - <<'EOF'
for p, old, new in [
    ('core/build.gradle', 'sourceSets.main.java.srcDir "../android/app/src/main/kotlin/uniffi"', 'sourceSets.main.java.srcDir "src/main/kotlin/uniffi"'),
    ('core/build.gradle', 'sourceSets.test.java.srcDir "../android/app/src/main/kotlin/uniffi"', 'sourceSets.test.java.srcDir "src/test/kotlin/uniffi"'),
    ('app-native/build.gradle', 'android.sourceSets.main.java.srcDir "../android/app/src/main/kotlin/uniffi"', '// bindings live in :core'),
    ('desktopApp/build.gradle', 'sourceSets.main.java.srcDir "../android/app/src/main/kotlin/uniffi"', 'sourceSets.main.java.srcDir "../core/src/main/kotlin/uniffi"'),
    ('rust/build-uniffi.sh', '--out-dir ../android/app/src/main/kotlin', '--out-dir ../core/src/main/kotlin/uniffi'),
    ('app-native/cargokit-standalone.gradle', 'def cargokitDir = file("../rust_builder/cargokit")', 'def cargokitDir = file("../rust_builder/cargokit")'),
]:
    s = open(p).read().replace(old, new)
    open(p, 'w').write(s)
# checkUniffiBindings committed-path fix
p = 'app-native/cargokit-standalone.gradle'
s = open(p).read()
s = s.replace('def committed = file("../android/app/src/main/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt")',
              'def committed = file("../core/src/main/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt")')
open(p, 'w').write(s)
# db seed model
import shutil
shutil.copy('lib/generated/objectbox-model.json', 'db/seed-objectbox-model.json')
p = 'tools/gen_db_entities.py'
s = open(p).read().replace('MODEL = "lib/generated/objectbox-model.json"', 'MODEL = "db/seed-objectbox-model.json"')
open(p, 'w').write(s)
p = 'db/build.gradle'
s = open(p).read().replace('def seed = rootProject.file("../lib/generated/objectbox-model.json")',
                           'def seed = rootProject.file("../db/seed-objectbox-model.json")')
open(p, 'w').write(s)
print('relocations done')
EOF
git add -A && git commit -m "cutover: relocate uniffi bindings into :core; seed model into db/"

# ---------------------------------------------------------------- phase 3
confirm "delete Flutter app (lib/, android/, windows/, pubspec, rust_builder platform dirs)"
git rm -r -q lib android windows pubspec.yaml pubspec.lock flutter_rust_bridge.yaml \
    rust_builder/android rust_builder/ios rust_builder/linux rust_builder/macos rust_builder/windows \
    rust_builder/pubspec.yaml 2>/dev/null || true
git add -A && git commit -m "cutover: remove the Flutter app (rust_builder/cargokit retained for the native build)"

# ---------------------------------------------------------------- phase 4
confirm "ci (drop Flutter build workflow)"
git rm -q .github/workflows/build.yml
git add -A && git commit -m "cutover: native.yml is the only CI"

echo
echo "Cutover branch prepared. VERIFY (device login + store upgrade path)"
echo "before merging to main — see tools/CUTOVER.md."
