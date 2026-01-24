package app.spread.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spread.domain.*
import app.spread.ui.theme.SpreadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpreadTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: ReaderViewModel = viewModel()
                    val state by viewModel.state.collectAsState()

                    // Load demo book on first launch (for testing)
                    LaunchedEffect(Unit) {
                        if (state.book == null) {
                            viewModel.loadBook(createDemoBook())
                        }
                    }

                    var showSettings by remember { mutableStateOf(false) }

                    ReaderScreen(
                        state = state,
                        onToggle = viewModel::toggle,
                        onPrev = viewModel::prevWord,
                        onNext = viewModel::nextWord,
                        onSeek = viewModel::seekChapter,
                        onWpmChange = viewModel::setWpm,
                        onSettingsClick = { showSettings = true }
                    )

                    if (showSettings) {
                        SettingsSheet(
                            settings = state.settings,
                            effectiveWpmInfo = state.effectiveWpmInfo,
                            onDismiss = { showSettings = false },
                            onSettingsChange = { action -> viewModel.dispatch(action) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Demo book for testing. Remove when file parsing is implemented.
 */
private fun createDemoBook(): Book {
    val sampleText = """
        Speed reading is a collection of reading methods which attempt to increase rates of reading
        without greatly reducing comprehension or retention. Methods include chunking and minimizing
        subvocalization. The many available speed reading training programs may use books, videos,
        software, and seminars.

        The average adult reading rate is between 200 and 300 words per minute. Speed readers claim
        to reach speeds of over 1000 words per minute while maintaining good comprehension. However,
        research suggests that there is a trade-off between speed and accuracy.

        RSVP, which stands for Rapid Serial Visual Presentation, is a method of displaying text one
        word at a time at a fixed focal point. This eliminates the need for eye movements and allows
        for faster reading speeds. The technique was developed in the 1970s and has been used in
        various speed reading applications.
    """.trimIndent()

    val paragraphs = sampleText.split("\n\n").map { it.replace("\n", " ").trim() }

    val chapter1 = createChapter(
        index = 0,
        title = "Introduction to Speed Reading",
        paragraphs = paragraphs
    )

    val chapter2 = createChapter(
        index = 1,
        title = "The Science Behind RSVP",
        paragraphs = listOf(
            "The human eye can only focus on a small area at a time, called the foveal region. Traditional reading requires moving this focal point across lines of text, which takes time.",
            "RSVP eliminates eye movement by presenting words at a fixed point. The brain can process words faster when the eye doesn't need to move.",
            "Studies show that comprehension begins to decrease at speeds above 500-600 words per minute for most readers. However, with practice, many people can improve both speed and comprehension."
        )
    )

    return createBook(
        id = "demo-book",
        title = "Understanding Speed Reading",
        author = "Demo Author",
        chapters = listOf(chapter1, chapter2)
    )
}
