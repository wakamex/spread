package app.spread.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.spread.data.BookRepository
import app.spread.data.NativeParser
import app.spread.data.SettingsRepository
import app.spread.data.toDomain
import app.spread.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val bookRepository = BookRepository.getInstance(application)

    private val _state = MutableStateFlow(ReaderState.Initial)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var saveSettingsJob: Job? = null
    private var saveProgressJob: Job? = null
    private var reparseJob: Job? = null

    init {
        // Load saved settings on startup
        viewModelScope.launch {
            settingsRepository.settings.first().let { savedSettings ->
                dispatch(Action.SettingsLoaded(savedSettings))
            }
        }
    }

    fun dispatch(action: Action) {
        val (newState, effects) = reduce(_state.value, action)
        _state.value = newState

        // Execute effects
        effects.forEach { effect ->
            when (effect) {
                is Effect.ScheduleTick -> scheduleTick(effect.delayMs)
                Effect.CancelTick -> cancelTick()
                is Effect.SaveProgress -> saveProgress(effect.bookId, effect.position)
                is Effect.SaveSettings -> saveSettings(effect.settings)
                is Effect.ReparseBook -> reparseBook(effect)
            }
        }
    }

    private fun saveSettings(settings: TimingSettings) {
        // Debounce saves to avoid excessive writes during slider dragging
        saveSettingsJob?.cancel()
        saveSettingsJob = viewModelScope.launch {
            delay(500) // Wait 500ms after last change
            settingsRepository.saveSettings(settings)
        }
    }

    private fun saveProgress(bookId: BookId, position: Position) {
        // Debounce progress saves to avoid excessive writes
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch {
            delay(500) // Wait 500ms after last change
            bookRepository.saveProgress(bookId.value, position)
        }
    }

    private fun reparseBook(effect: Effect.ReparseBook) {
        // Cancel any pending re-parse
        reparseJob?.cancel()
        reparseJob = viewModelScope.launch(Dispatchers.IO) {
            val newBook = when (val source = effect.source) {
                is BookSource.Epub -> {
                    NativeParser.parseEpubWithConfig(source.bytes, effect.maxChunkChars)
                        ?.toDomain(source.bookId)
                }
                is BookSource.Demo -> {
                    createDemoBook(effect.maxChunkChars)
                }
            }

            if (newBook != null) {
                val newPosition = mapPositionAfterReparse(
                    effect.currentPosition,
                    effect.currentBook,
                    newBook
                )
                withContext(Dispatchers.Main) {
                    dispatch(Action.BookReparsed(newBook, newPosition))
                }
            }
        }
    }

    private fun scheduleTick(delayMs: Long) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            delay(delayMs)
            if (isActive) {
                dispatch(Action.NextWord)
            }
        }
    }

    private fun cancelTick() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // Convenience methods for UI
    fun play() = dispatch(Action.Play)
    fun pause() = dispatch(Action.Pause)
    fun toggle() = dispatch(Action.Toggle)
    fun nextWord() = dispatch(Action.NextWord)
    fun prevWord() = dispatch(Action.PrevWord)
    fun setWpm(wpm: Int) = dispatch(Action.SetBaseWpm(wpm))
    fun jumpToChapter(index: Int) = dispatch(Action.JumpToChapter(index))
    fun seekChapter(fraction: Float) = dispatch(Action.SeekChapter(fraction))

    fun loadBook(book: Book, source: BookSource, fileUri: Uri? = null) {
        dispatch(Action.BookLoaded(book, source))

        // Save to library and restore progress
        viewModelScope.launch {
            // Save to library if fileUri provided
            fileUri?.let {
                bookRepository.saveBookToLibrary(book, it)
            }

            // Update last opened timestamp
            bookRepository.updateLastOpened(book.id.value)

            // Restore saved progress
            bookRepository.getProgress(book.id.value)?.let { savedPosition ->
                dispatch(Action.RestorePosition(savedPosition))
            }
        }
    }

    fun closeBook() = dispatch(Action.BookClosed)
    fun restartBook() = dispatch(Action.RestartBook)

    override fun onCleared() {
        super.onCleared()
        cancelTick()
    }
}
