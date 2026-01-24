# Spread Code Style Guide

## Constants

### No Magic Numbers
Avoid hardcoded numeric values in code. Define named constants with clear documentation.

**Bad:**
```kotlin
if (word.length > 12) { ... }
assertTrue(cleanLen <= 12)
```

**Good:**
```kotlin
object WordSplitConfig {
    /** Maximum characters per chunk (fits 320dp screens at 48sp font) */
    const val MAX_CHUNK_CHARS = 10
}

if (word.length > WordSplitConfig.MAX_CHUNK_CHARS) { ... }
assertTrue(cleanLen <= WordSplitConfig.MAX_CHUNK_CHARS)
```

### Cross-Language Constants
When the same constant exists in both Kotlin and Rust:
1. Add `SYNC:` comment referencing the other file
2. Add a test that verifies consistency if possible

```kotlin
// SYNC: Must match rust/src/tokenizer.rs
const val MAX_CHUNK_CHARS = 10
```

```rust
/// SYNC: Must match Kotlin Tokenizer.kt
const MAX_CHUNK_CHARS: usize = 10;
```

## Testing

### Test Against Actual Constants
Tests should import and use the same constants as production code, not hardcoded values.

**Bad:**
```kotlin
assertTrue(chunk.length <= 12)  // What if MAX_CHUNK_CHARS changes?
```

**Good:**
```kotlin
assertTrue(chunk.length <= WordSplitConfig.MAX_CHUNK_CHARS)
```

### Document Test Assumptions
If a test depends on specific constant values, document why.

```kotlin
@Test
fun `10 char word is not split`() {
    // Test boundary: MAX_CHUNK_CHARS is 10, MIN_SPLIT_LENGTH is 11
    // So a 10-char word should NOT be split
    val words = tokenize("understand")  // exactly 10 chars
    assertEquals(1, words.size)
}
```
