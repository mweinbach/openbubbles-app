#!/bin/bash
set -euo pipefail
cargo build --release --locked
# cdylib artifact suffix differs by platform.
LIB=""
for candidate in \
    target/release/librust_lib_bluebubbles.so \
    target/release/librust_lib_bluebubbles.dylib \
    target/release/rust_lib_bluebubbles.dll
do
    if [ -f "$candidate" ]; then
        LIB="$candidate"
        break
    fi
done
if [ -z "$LIB" ]; then
    echo "cdylib not found in target/release" >&2
    exit 1
fi
cargo run --locked --bin uniffi-bindgen generate --library "$LIB" --language kotlin --out-dir ../core/src/main/kotlin
GENERATED_BINDING=../core/src/main/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt
perl -pi -e 's/[ \t]+$//' "$GENERATED_BINDING"
mkdir -p ../core/src/test/kotlin/uniffi/rust_lib_bluebubbles
cp \
    "$GENERATED_BINDING" \
    ../core/src/test/kotlin/uniffi/rust_lib_bluebubbles/rust_lib_bluebubbles.kt
