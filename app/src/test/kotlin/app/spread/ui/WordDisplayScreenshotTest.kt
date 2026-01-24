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
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "comprehension",
                showCenterLine = true
            )
        }
    }

    @Test
    fun wordDisplay_veryLongWord() {
        paparazzi.snapshot {
            WordDisplayTestable(
                word = "subvocalization",
                showCenterLine = true
            )
        }
    }

    @Test
    fun wordDisplay_problemWords() {
        // Words that were reported as "way off"
        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                WordDisplayTestable(word = "comprehens", showCenterLine = true)
                WordDisplayTestable(word = "subvocal", showCenterLine = true)
                WordDisplayTestable(word = "available", showCenterLine = true)
            }
        }
    }

    @Test
    fun wordDisplay_allLengths() {
        // Test words of varying lengths to verify alignment
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
            "comprehens",  // 10 chars
            "recognition", // 11 chars
            "presentation",// 12 chars
            "comprehension",// 13 chars
            "infrastructure",// 14 chars
            "subvocalization" // 15 chars
        )

        paparazzi.snapshot {
            Column(modifier = Modifier.fillMaxWidth()) {
                testWords.forEach { word ->
                    WordDisplayTestable(word = word, showCenterLine = true)
                }
            }
        }
    }
}
