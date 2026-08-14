#!/bin/bash
set -e
cargo build --release
# cdylib artifact suffix differs by platform (.so on Linux/Android, .dylib on macOS)
LIB=$(ls target/release/librust_lib_bluebubbles.* 2>/dev/null | grep -E '\.(so|dylib)$' | head -1)
if [ -z "$LIB" ]; then
    echo "cdylib not found in target/release" >&2
    exit 1
fi
cargo run --bin uniffi-bindgen generate --library "$LIB" --language kotlin --out-dir ../android/app/src/main/kotlin
