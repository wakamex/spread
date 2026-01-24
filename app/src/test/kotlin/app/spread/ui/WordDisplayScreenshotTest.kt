package app.spread.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.spread.domain.tokenize
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
        // "subvocalization" (15 chars) gets split by tokenizer
        val words = tokenize("subvocalization")
        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                words.forEach { word ->
                    WordDisplayTestable(word = word.text, showCenterLine = true)
                }
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
    fun wordDisplay_splitChunks_internationalization() {
        // Use actual tokenizer for "internationalization" (20 chars)
        val words = tokenize("internationalization")
        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                words.forEach { word ->
                    WordDisplayTestable(
                        word = word.text,
                        showCenterLine = true,
                        showBoundaries = true
                    )
                }
            }
        }
    }

    @Test
    fun wordDisplay_splitChunks_variousLongWords() {
        // Test actual tokenizer output for various long words
        val longWords = listOf(
            "counterrevolutionary",     // 20 chars
            "deinstitutionalization",   // 22 chars
            "electroencephalography",   // 22 chars
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                longWords.forEach { longWord ->
                    val words = tokenize(longWord)
                    words.forEach { word ->
                        WordDisplayTestable(
                            word = word.text,
                            showCenterLine = true,
                            showBoundaries = true
                        )
                    }
                }
            }
        }
    }

    @Test
    fun wordDisplay_realWorldSentence() {
        // Use tokenizer for a real sentence with mixed word lengths
        val sentence = "The quick brown fox jumped over the lazy dog"
        val words = tokenize(sentence)

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                words.forEach { word ->
                    WordDisplayTestable(word = word.text, showCenterLine = true)
                }
            }
        }
    }

    @Test
    fun wordDisplay_sentenceWithLongWords() {
        // Use tokenizer for sentence containing long words
        val sentence = "The internationalization of telecommunications requires professionalization"
        val words = tokenize(sentence)

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                words.forEach { word ->
                    WordDisplayTestable(
                        word = word.text,
                        showCenterLine = true,
                        showBoundaries = true
                    )
                }
            }
        }
    }

}
