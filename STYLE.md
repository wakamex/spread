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
    /** Max letters per chunk (10 + 2 hyphens = 12 display chars) */
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

## Word Splitting

### Cognitive-First, Font-Size-Adaptive
Split words at 10 letters max (within Research.md visual span of 10-12 chars).
With hyphens, max display is 12 chars. Adapt font size per orientation to fit.

**Why not split smaller for narrow screens?**
- Inconsistent reading speed across orientations
- More complex display-time logic
- 10-12 chars is within optimal cognitive range

**Font size calculation:**
```kotlin
// To fit MAX_DISPLAY_CHARS (12) on screen:
val availableWidthDp = screenWidthDp - paddingDp
val charWidthDp = availableWidthDp / MAX_DISPLAY_CHARS
val fontSp = baseFontSp * (charWidthDp / baseCharWidthDp)

// Example for 320dp narrow portrait:
// (320 - 16) / 12 = 25.3dp per char
// 48sp * (25.3 / 29) ≈ 42sp
```

### Key Constants
- `MAX_CHUNK_CHARS = 10`: Max letters per chunk
- `MAX_DISPLAY_CHARS = 12`: With hyphens (10 + 2), fits 320dp screens
- `MIN_SPLIT_LENGTH = 11`: Words < 11 chars stay whole
- `MIN_CHUNK_CHARS = 3`: Avoid tiny fragments

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
