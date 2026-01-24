package app.spread.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test

class WordDisplayScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar.Fullscreen"
    )

    @Test
    fun wordDisplay_shortWord() {
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "the",
                showCenterLine = true
            )
        }
    }

    @Test
    fun wordDisplay_mediumWord() {
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "reading",
                showCenterLine = true
            )
        }
    }

    @Test
    fun wordDisplay_longWord() {
        // 12 chars - just under split threshold, displayed as-is
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "presentation",
                showCenterLine = true
            )
        }
    }

    @Test
    fun wordDisplay_splitWord() {
        // "subvocalization" (15 chars) would be split by Rust
        // Testing the chunks it produces: "sub-", "vocaliz-", "-ation"
        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                WordDisplayTestable(word = "sub-", showCenterLine = true)
                WordDisplayTestable(word = "vocaliz-", showCenterLine = true)
                WordDisplayTestable(word = "-ation", showCenterLine = true)
            }
        }
    }

    @Test
    fun wordDisplay_boundaryWords() {
        // Words at or near the 12-char boundary (split threshold is 13)
        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                WordDisplayTestable(word = "comprehend", showCenterLine = true)   // 10 chars
                WordDisplayTestable(word = "recognition", showCenterLine = true)  // 11 chars
                WordDisplayTestable(word = "presentation", showCenterLine = true) // 12 chars - max unsplit
            }
        }
    }

    @Test
    fun wordDisplay_allLengths() {
        // Test words of varying lengths to verify alignment
        // These are words that won't be split (< 13 chars)
        val testWords = listOf(
            "I",           // 1 char
            "to",          // 2 chars
            "the",         // 3 chars
            "word",        // 4 chars
            "speed",       // 5 chars
            "reader",      // 6 chars
            "reading",     // 7 chars
            "possible",    // 8 chars
            "beautiful",   // 9 chars
            "comprehend",  // 10 chars
            "recognition", // 11 chars
            "presentation" // 12 chars - max unsplit word
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                testWords.forEach { word ->
                    WordDisplayTestable(word = word, showCenterLine = true)
                }
            }
        }
    }

    @Test
    fun wordDisplay_splitChunks_withHyphens() {
        // Test hyphenated chunks as produced by Rust morpheme splitter
        // "internationalization" -> "inter-", "national-", "-ization"
        val splitChunks = listOf(
            "inter-",      // prefix chunk
            "national-",   // middle chunk
            "-ization",    // suffix chunk
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                splitChunks.forEach { chunk ->
                    WordDisplayTestable(
                        word = chunk,
                        showCenterLine = true,
                        showBoundaries = true
                    )
                }
            }
        }
    }

    @Test
    fun wordDisplay_splitChunks_variousPatterns() {
        // Test different split patterns from Rust parser
        val chunks = listOf(
            // "unbelievability" -> "un-", "believabil-", "-ity"
            "un-",
            "believabil-",
            "-ity",
            // "counterrevolutionary" -> "counter-", "revolution-", "-ary"
            "counter-",
            "revolution-",
            "-ary",
            // "deinstitutionalization" -> "de-", "institution-", "-alization"
            "de-",
            "institution-",
            "-alization",
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                chunks.forEach { chunk ->
                    WordDisplayTestable(
                        word = chunk,
                        showCenterLine = true,
                        showBoundaries = true
                    )
                }
            }
        }
    }

    @Test
    fun wordDisplay_realWorldSentence() {
        // Simulate a real sentence with mixed word lengths
        // Short words shown as-is, long words shown as chunks
        val words = listOf(
            "The",
            "quick",
            "brown",
            "fox",
            "jumped",
            "over",
            "the",
            "lazy",
            "dog",
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                words.forEach { word ->
                    WordDisplayTestable(word = word, showCenterLine = true)
                }
            }
        }
    }
}
