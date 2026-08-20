#!/bin/bash
set -euo pipefail
# UniFFI extracts exported metadata, which is identical across Cargo profiles.
# Keep binding generation off the shipping release profile's LTO path.
cargo build --locked
# cdylib artifact suffix differs by platform.
LIB=""
for candidate in \
    target/debug/librust_lib_bluebubbles.so \
    target/debug/librust_lib_bluebubbles.dylib \
    target/debug/rust_lib_bluebubbles.dll
do
    if [ -f "$candidate" ]; then
        LIB="$candidate"
        break
    fi
done
if [ -z "$LIB" ]; then
    echo "cdylib not found in target/debug" >&2
    exit 1
fi
cargo run --locked --bin uniffi-bindgen generate --library "$LIB" --language kotlin --out-dir ../core/src/main/kotlin
GENERATED_BINDING=../core/src/main/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt
perl -pi -e 's/[ \t]+$//' "$GENERATED_BINDING"
mkdir -p ../core/src/test/kotlin/uniffi/rust_lib_bluebubbles
cp \
    "$GENERATED_BINDING" \
    ../core/src/test/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt
