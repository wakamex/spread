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
                            val result = loadDemoBook(context, state.settings.maxDisplayChars)
                            if (result != null) {
                                val (book, source) = result
                                viewModel.loadBook(book, source)
                            }
                        }
                    }

                    ReaderScreen(
                        state = state,
                        onToggle = viewModel::toggle,
                        onSeek = viewModel::seekChapter,
                        onWpmChange = viewModel::setWpm,
                        onSettingsClick = { showSettings = true },
                        onOpenBook = {
                            filePickerLauncher.launch(arrayOf(
                                "application/epub+zip",
                                "application/octet-stream"
                            ))
                        },
                        onRestart = viewModel::restartBook,
                        onSkipWords = viewModel::skipWords,
                        onPrevChapter = viewModel::prevChapter,
                        onNextChapter = viewModel::nextChapter,
                        onJumpToChapter = viewModel::jumpToChapter
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
): Pair<Book, BookSource>? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext null

        val bytes = inputStream.use { it.readBytes() }

        val nativeBook = NativeParser.parseEpubWithConfig(bytes, maxChunkChars)
            ?: return@withContext null

        // Generate a unique ID for this book
        val bookId = UUID.randomUUID().toString()
        val book = nativeBook.toDomain(bookId)
        val source = BookSource(bytes, bookId)

        Pair(book, source)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Load the demo book from assets.
 */
private suspend fun loadDemoBook(
    context: android.content.Context,
    maxChunkChars: Int
): Pair<Book, BookSource>? = withContext(Dispatchers.IO) {
    try {
        val bytes = context.assets.open("demo.epub").use { it.readBytes() }

        val nativeBook = NativeParser.parseEpubWithConfig(bytes, maxChunkChars)
            ?: return@withContext null

        val bookId = "demo-book"
        val book = nativeBook.toDomain(bookId)
        val source = BookSource(bytes, bookId)

        Pair(book, source)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

