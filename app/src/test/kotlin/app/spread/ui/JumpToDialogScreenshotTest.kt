package app.spread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.spread.domain.*
import org.junit.Rule
import org.junit.Test

class JumpToDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar.Fullscreen"
    )

    private val sampleWords = listOf(
        Word("The", LengthBucket.SHORT, null),
        Word("quick", LengthBucket.MEDIUM, null),
        Word("brown", LengthBucket.MEDIUM, null),
        Word("fox", LengthBucket.SHORT, null),
        Word("jumps", LengthBucket.MEDIUM, null),
        Word("over", LengthBucket.SHORT, null),
        Word("the", LengthBucket.SHORT, null),
        Word("lazy", LengthBucket.SHORT, null),
        Word("dog", LengthBucket.SHORT, Punctuation.PERIOD)
    )

    private val chapterStats = ChapterStats.fromWords(sampleWords)

    private val chapter = Chapter(
        index = 0,
        title = "Chapter 1: The Beginning",
        words = sampleWords,
        stats = chapterStats
    )

    private val book = Book(
        id = BookId("test"),
        metadata = BookMetadata(title = "Test Book", author = "Author", coverPath = null),
        chapters = listOf(chapter),
        stats = BookStats.fromChapters(listOf(chapter))
    )

    private val effectiveWpm = EffectiveWpmInfo(
        chapter = EffectiveWpm(wpm = 247, totalMinutes = 12.5, minutesRemaining = 8.3),
        book = EffectiveWpm(wpm = 245, totalMinutes = 180.0, minutesRemaining = 120.0)
    )

    private val state = ReaderState(
        book = book,
        bookSource = null,
        position = Position(chapterIndex = 0, wordIndex = 3),
        settings = TimingSettings.Default,
        settingsLoaded = true,
        playing = false,
        effectiveWpmInfo = effectiveWpm
    )

    @Test
    fun readerScreen_paused() {
        paparazzi.snapshot {
            ReaderScreen(
                state = state,
                onToggle = {},
                onSeek = {},
                onWpmChange = {},
                onSettingsClick = {}
            )
        }
    }

    @Test
    fun jumpToDialog_percentageMode() {
        paparazzi.snapshot {
            DialogFrame {
                JumpToDialogBody(mode = "Percentage")
            }
        }
    }

    @Test
    fun jumpToDialog_timeMode() {
        paparazzi.snapshot {
            DialogFrame {
                JumpToDialogBody(mode = "Time")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DialogFrame(content: @androidx.compose.runtime.Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(24.dp)
    ) {
        Text(
            text = "Jump to position",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.padding(bottom = 16.dp)
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            TextButton(onClick = {}) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
            TextButton(onClick = {}) {
                Text("Go", color = Color.White)
            }
        }
    }
}
