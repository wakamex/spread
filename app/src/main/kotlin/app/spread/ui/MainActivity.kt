package app.spread.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spread.data.NativeParser
import app.spread.data.toDomain
import app.spread.domain.*
import app.spread.ui.theme.SpreadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpreadTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val context = LocalContext.current
                    val viewModel: ReaderViewModel = viewModel()
                    val state by viewModel.state.collectAsState()
                    val scope = rememberCoroutineScope()

                    var showSettings by remember { mutableStateOf(false) }
                    var isLoading by remember { mutableStateOf(false) }

                    // File picker launcher
                    val filePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        uri?.let {
                            isLoading = true
                            scope.launch {
                                val result = loadEpubFromUri(context, uri, state.settings.maxDisplayChars)
                                isLoading = false
                                if (result != null) {
                                    val (book, source) = result
                                    viewModel.loadBook(book, source, fileUri = uri)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to parse EPUB",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    // Load demo book after settings are ready
                    LaunchedEffect(state.settingsLoaded) {
                        if (state.settingsLoaded && state.book == null) {
                            val book = createDemoBook(state.settings.maxDisplayChars)
                            viewModel.loadBook(book, BookSource.Demo)
                        }
                    }

                    ReaderScreen(
                        state = state,
                        onToggle = viewModel::toggle,
                        onPrev = viewModel::prevWord,
                        onNext = viewModel::nextWord,
                        onSeek = viewModel::seekChapter,
                        onWpmChange = viewModel::setWpm,
                        onSettingsClick = { showSettings = true },
                        onOpenBook = {
                            filePickerLauncher.launch(arrayOf(
                                "application/epub+zip",
                                "application/octet-stream"
                            ))
                        },
                        onRestart = viewModel::restartBook
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
 * Load and parse an EPUB file from a content URI.
 * Returns the parsed Book and its source for re-parsing.
 */
private suspend fun loadEpubFromUri(
    context: android.content.Context,
    uri: Uri,
    maxChunkChars: Int
): Pair<Book, BookSource.Epub>? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext null

        val bytes = inputStream.use { it.readBytes() }

        val nativeBook = NativeParser.parseEpubWithConfig(bytes, maxChunkChars)
            ?: return@withContext null

        // Generate a unique ID for this book
        val bookId = UUID.randomUUID().toString()
        val book = nativeBook.toDomain(bookId)
        val source = BookSource.Epub(bytes, bookId)

        Pair(book, source)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Demo book for first-time users to test the app.
 * Also used for re-parsing when chunk size changes.
 */
fun createDemoBook(maxChunkChars: Int = WordSplitConfig.DEFAULT_MAX_CHUNK_CHARS): Book {
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
        paragraphs = paragraphs,
        maxChunkChars = maxChunkChars
    )

    val chapter2 = createChapter(
        index = 1,
        title = "The Science Behind RSVP",
        paragraphs = listOf(
            "The human eye can only focus on a small area at a time, called the foveal region. Traditional reading requires moving this focal point across lines of text, which takes time.",
            "RSVP eliminates eye movement by presenting words at a fixed point. The brain can process words faster when the eye doesn't need to move.",
            "Studies show that comprehension begins to decrease at speeds above 500-600 words per minute for most readers. However, with practice, many people can improve both speed and comprehension."
        ),
        maxChunkChars = maxChunkChars
    )

    // Chapter with long words to test morpheme splitting
    val chapter3 = createChapter(
        index = 2,
        title = "Testing Long Words",
        paragraphs = listOf(
            "This chapter tests internationalization of telecommunications infrastructure. The professionalization of environmentalism requires conceptualization.",
            "We must address misunderstandings about responsibilities in deinstitutionalization. These counterrevolutionary incomprehensibilities need clarification.",
            "The compartmentalization of interdisciplinary electroencephalography demonstrates psychophysiological characteristics of neuropsychological experimentation.",
            "Characteristically, overcompensation and misrepresentation lead to disproportionate counterproductivity in any organizational restructuring."
        ),
        maxChunkChars = maxChunkChars
    )

    return createBook(
        id = "demo-book",
        title = "Understanding Speed Reading",
        author = "Demo Author",
        chapters = listOf(chapter1, chapter2, chapter3)
    )
}
