#!/bin/bash
#
# Build Rust library for Android
# Requires: rustup, cargo-ndk, Android NDK
#
# Install prerequisites:
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
#   cargo install cargo-ndk
#
# Set ANDROID_NDK_HOME to your NDK path

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Output directory for .so files
OUT_DIR="../app/src/main/jniLibs"

# Minimum Android API level (21 = Lollipop)
MIN_SDK=21

echo "Building Spread Core for Android..."

# Build for all target architectures
cargo ndk \
    -t armeabi-v7a \
    -t arm64-v8a \
    -t x86 \
    -t x86_64 \
    -o "$OUT_DIR" \
    --manifest-path Cargo.toml \
    build --release

echo ""
echo "Build complete! Libraries at:"
find "$OUT_DIR" -name "*.so" -type f

echo ""
echo "Library sizes:"
find "$OUT_DIR" -name "*.so" -type f -exec ls -lh {} \;
