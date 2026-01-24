package app.spread.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.spread.domain.tokenize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tests for narrow screens (320dp width).
 * Verifies font scaling works correctly to fit MAX_CHUNK_CHARS.
 */
class NarrowScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        // Narrow device: 320dp width
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 320,
            screenHeight = 640
        ),
        theme = "android:Theme.Material.NoActionBar.Fullscreen"
    )

    @Test
    fun narrowScreen_maxChunkWord() {
        // 12 chars - exactly MAX_CHUNK_CHARS, should fit with scaled font
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "presentation",  // 12 chars
                showCenterLine = true,
                showBoundaries = true
            )
        }
    }

    @Test
    fun narrowScreen_splitChunks() {
        // Verify split chunks fit on narrow screen
        val words = tokenize("counterrevolutionary")
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
    fun narrowScreen_allLengths() {
        // Test all word lengths on narrow screen
        val testWords = listOf(
            "I",           // 1 char
            "word",        // 4 chars
            "reading",     // 7 chars
            "comprehend",  // 10 chars
            "presentation" // 12 chars - MAX_CHUNK_CHARS
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                testWords.forEach { word ->
                    WordDisplayTestable(
                        word = word,
                        showCenterLine = true,
                        showBoundaries = true
                    )
                }
            }
        }
    }

    @Test
    fun narrowScreen_longWordChunks() {
        // Test that all chunks from long words fit on narrow screen
        val longWords = listOf(
            "internationalization",
            "electroencephalography",
            "deinstitutionalization"
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                longWords.forEach { longWord ->
                    tokenize(longWord).forEach { word ->
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
}
