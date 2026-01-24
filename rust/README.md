# Spread Core - Rust Native Library

EPUB parser and text tokenizer for Spread.

## Pre-built Binaries

The library is pre-compiled for all Android ABIs in `../app/src/main/jniLibs/`:
- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

## Rebuilding

Requires [cargo-ndk](https://github.com/aspect-build/cargo-ndk):

```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

Then build:

```bash
./build-android.sh
```

Or manually:

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 -o ../app/src/main/jniLibs build --release
```

## Testing

```bash
cargo test
```
