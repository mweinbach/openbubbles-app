#!/bin/bash
set -e
cargo build --release
cargo run --bin uniffi-bindgen generate --library target/release/librust_lib_bluebubbles.so --language kotlin --out-dir ../android/app/src/main/kotlin