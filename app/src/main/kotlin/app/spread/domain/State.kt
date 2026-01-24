package app.spread.domain

/**
 * Reader state and pure state transitions.
 */

data class ReaderState(
    val book: Book?,
    val position: Position,
    val settings: TimingSettings,
    val playing: Boolean,
    val effectiveWpmInfo: EffectiveWpmInfo?
) {
    val currentChapter: Chapter?
        get() = book?.chapters?.getOrNull(position.chapterIndex)

    val currentWord: Word?
        get() = currentChapter?.words?.getOrNull(position.wordIndex)

    val hasNextWord: Boolean
        get() {
            val book = book ?: return false
            val chapter = currentChapter ?: return false
            return position.wordIndex + 1 < chapter.words.size ||
                    position.chapterIndex + 1 < book.chapters.size
        }

    val hasPrevWord: Boolean
        get() = position.wordIndex > 0 || position.chapterIndex > 0

    /** True when at the last word of the last chapter (book finished) */
    val isAtEnd: Boolean
        get() {
            val book = book ?: return false
            val chapter = currentChapter ?: return false
            return position.chapterIndex == book.chapters.lastIndex &&
                    position.wordIndex == chapter.words.lastIndex
        }

    val progress: Progress
        get() {
            val book = book ?: return Progress(0f, 0f)
            val chapter = currentChapter ?: return Progress(0f, 0f)

            val chapterProgress = if (chapter.words.isNotEmpty()) {
                position.wordIndex.toFloat() / chapter.words.size
            } else 0f

            val totalWords = book.stats.totalWords
            if (totalWords == 0) return Progress(chapterProgress, 0f)

            val wordsBeforeChapter = book.chapters
                .take(position.chapterIndex)
                .sumOf { it.stats.wordCount }
            val bookProgress = (wordsBeforeChapter + position.wordIndex).toFloat() / totalWords

            return Progress(chapterProgress, bookProgress)
        }

    companion object {
        val Initial = ReaderState(
            book = null,
            position = Position.START,
            settings = TimingSettings.Default,
            playing = false,
            effectiveWpmInfo = null
        )
    }
}

data class Progress(
    val chapter: Float,  // 0.0 to 1.0
    val book: Float      // 0.0 to 1.0
)

// --- Actions ---

sealed interface Action {
    // Playback
    data object Play : Action
    data object Pause : Action
    data object Toggle : Action

    // Navigation
    data object NextWord : Action
    data object PrevWord : Action
    data class JumpToChapter(val index: Int) : Action
    data class JumpToPosition(val position: Position) : Action
    data class SeekChapter(val fraction: Float) : Action  // 0.0 to 1.0

    // Settings
    data class SetBaseWpm(val wpm: Int) : Action
    data class SetPeriodDelay(val ms: Int) : Action
    data class SetCommaDelay(val ms: Int) : Action
    data class SetParagraphDelay(val ms: Int) : Action
    data class SetMediumWordExtra(val ms: Int) : Action
    data class SetLongWordExtra(val ms: Int) : Action
    data class SetVeryLongWordExtra(val ms: Int) : Action
    data class SetSplitChunkMultiplier(val multiplier: Float) : Action
    data class SetAnchorPosition(val percent: Float) : Action
    data class SetVerticalPositionPortrait(val percent: Float) : Action
    data class SetVerticalPositionLandscape(val percent: Float) : Action
    data class ApplyPreset(val preset: TimingSettings) : Action

    // Content
    data class BookLoaded(val book: Book) : Action
    data object BookClosed : Action
    data object RestartBook : Action

    // Persistence
    data class SettingsLoaded(val settings: TimingSettings) : Action
    data class RestorePosition(val position: Position) : Action  // Restore without saving
}

// --- Effects (side effects as data) ---

sealed interface Effect {
    data class ScheduleTick(val delayMs: Long) : Effect
    data object CancelTick : Effect
    data class SaveProgress(val bookId: BookId, val position: Position) : Effect
    data class SaveSettings(val settings: TimingSettings) : Effect
}

// --- Reducer output ---

data class Update(
    val state: ReaderState,
    val effects: List<Effect> = emptyList()
)

// --- Pure reducer ---

fun reduce(state: ReaderState, action: Action): Update {
    return when (action) {
        Action.Play -> {
            if (state.book == null || !state.hasNextWord) {
                Update(state)
            } else {
                val delay = state.currentWord?.delayMs(state.settings) ?: state.settings.baseDelayMs.toLong()
                Update(
                    state = state.copy(playing = true),
                    effects = listOf(Effect.ScheduleTick(delay))
                )
            }
        }

        Action.Pause -> Update(
            state = state.copy(playing = false),
            effects = listOf(Effect.CancelTick)
        )

        Action.Toggle -> reduce(state, if (state.playing) Action.Pause else Action.Play)

        Action.NextWord -> {
            val newPosition = state.nextPosition()
            if (newPosition == null) {
                // End of book
                Update(
                    state = state.copy(playing = false).recalculateWpm(),
                    effects = listOf(Effect.CancelTick)
                )
            } else {
                val newState = state.copy(position = newPosition).recalculateWpm()
                val effects = buildList {
                    if (state.playing) {
                        val delay = newState.currentWord?.delayMs(state.settings)
                            ?: state.settings.baseDelayMs.toLong()
                        add(Effect.ScheduleTick(delay))
                    }
                    state.book?.id?.let { add(Effect.SaveProgress(it, newPosition)) }
                }
                Update(newState, effects)
            }
        }

        Action.PrevWord -> {
            val newPosition = state.prevPosition() ?: state.position
            Update(
                state = state.copy(position = newPosition).recalculateWpm(),
                effects = state.book?.id?.let { listOf(Effect.SaveProgress(it, newPosition)) } ?: emptyList()
            )
        }

        is Action.JumpToChapter -> {
            val book = state.book ?: return Update(state)
            val index = action.index.coerceIn(0, book.chapters.lastIndex)
            val newPosition = Position(index, 0)
            Update(
                state = state.copy(position = newPosition, playing = false).recalculateWpm(),
                effects = listOf(
                    Effect.CancelTick,
                    Effect.SaveProgress(book.id, newPosition)
                )
            )
        }

        is Action.JumpToPosition -> {
            val newState = state.copy(position = action.position, playing = false).recalculateWpm()
            Update(
                state = newState,
                effects = buildList {
                    add(Effect.CancelTick)
                    state.book?.id?.let { add(Effect.SaveProgress(it, action.position)) }
                }
            )
        }

        is Action.SeekChapter -> {
            val chapter = state.currentChapter ?: return Update(state)
            val wordIndex = (action.fraction * chapter.words.size).toInt()
                .coerceIn(0, chapter.words.lastIndex.coerceAtLeast(0))
            val newPosition = state.position.copy(wordIndex = wordIndex)
            Update(
                state = state.copy(position = newPosition, playing = false).recalculateWpm(),
                effects = buildList {
                    add(Effect.CancelTick)
                    state.book?.id?.let { add(Effect.SaveProgress(it, newPosition)) }
                }
            )
        }

        is Action.SetBaseWpm -> {
            val wpm = action.wpm.coerceIn(100, 1500)
            val newSettings = state.settings.copy(baseWpm = wpm)
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetPeriodDelay -> {
            val newSettings = state.settings.copy(periodDelayMs = action.ms.coerceIn(0, 1000))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetCommaDelay -> {
            val newSettings = state.settings.copy(commaDelayMs = action.ms.coerceIn(0, 500))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetParagraphDelay -> {
            val newSettings = state.settings.copy(paragraphDelayMs = action.ms.coerceIn(0, 2000))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetMediumWordExtra -> {
            val newSettings = state.settings.copy(mediumWordExtraMs = action.ms.coerceIn(0, 200))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetLongWordExtra -> {
            val newSettings = state.settings.copy(longWordExtraMs = action.ms.coerceIn(0, 300))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetVeryLongWordExtra -> {
            val newSettings = state.settings.copy(veryLongWordExtraMs = action.ms.coerceIn(0, 500))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetSplitChunkMultiplier -> {
            val newSettings = state.settings.copy(splitChunkMultiplier = action.multiplier.coerceIn(1.0f, 2.0f))
            Update(
                state = state.copy(settings = newSettings).recalculateWpm(),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetAnchorPosition -> {
            val newSettings = state.settings.copy(anchorPositionPercent = action.percent.coerceIn(0.3f, 0.5f))
            Update(
                state = state.copy(settings = newSettings),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetVerticalPositionPortrait -> {
            val newSettings = state.settings.copy(verticalPositionPortrait = action.percent.coerceIn(0.1f, 0.5f))
            Update(
                state = state.copy(settings = newSettings),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.SetVerticalPositionLandscape -> {
            val newSettings = state.settings.copy(verticalPositionLandscape = action.percent.coerceIn(0.1f, 0.5f))
            Update(
                state = state.copy(settings = newSettings),
                effects = listOf(Effect.SaveSettings(newSettings))
            )
        }

        is Action.ApplyPreset -> Update(
            state = state.copy(settings = action.preset).recalculateWpm(),
            effects = listOf(Effect.SaveSettings(action.preset))
        )

        is Action.BookLoaded -> {
            val newState = state.copy(
                book = action.book,
                position = Position.START,
                playing = false
            ).recalculateWpm()
            Update(newState, listOf(Effect.CancelTick))
        }

        Action.BookClosed -> Update(
            state = state.copy(book = null, position = Position.START, playing = false, effectiveWpmInfo = null),
            effects = listOf(Effect.CancelTick)
        )

        Action.RestartBook -> {
            val book = state.book ?: return Update(state)
            Update(
                state = state.copy(position = Position.START, playing = false).recalculateWpm(),
                effects = listOf(
                    Effect.CancelTick,
                    Effect.SaveProgress(book.id, Position.START)
                )
            )
        }

        is Action.SettingsLoaded -> Update(
            state = state.copy(settings = action.settings).recalculateWpm()
        )

        is Action.RestorePosition -> {
            // Restore position without emitting SaveProgress (avoids circular save)
            val book = state.book ?: return Update(state)
            val chapter = book.chapters.getOrNull(action.position.chapterIndex) ?: return Update(state)
            val validPosition = Position(
                chapterIndex = action.position.chapterIndex,
                wordIndex = action.position.wordIndex.coerceIn(0, chapter.words.lastIndex.coerceAtLeast(0))
            )
            Update(
                state = state.copy(position = validPosition).recalculateWpm()
            )
        }
    }
}

// --- Helper functions ---

private fun ReaderState.nextPosition(): Position? {
    val book = book ?: return null
    val chapter = currentChapter ?: return null

    return when {
        position.wordIndex + 1 < chapter.words.size ->
            position.copy(wordIndex = position.wordIndex + 1)

        position.chapterIndex + 1 < book.chapters.size ->
            Position(chapterIndex = position.chapterIndex + 1, wordIndex = 0)

        else -> null
    }
}

private fun ReaderState.prevPosition(): Position? {
    val book = book ?: return null

    return when {
        position.wordIndex > 0 ->
            position.copy(wordIndex = position.wordIndex - 1)

        position.chapterIndex > 0 -> {
            val prevChapter = book.chapters[position.chapterIndex - 1]
            Position(
                chapterIndex = position.chapterIndex - 1,
                wordIndex = (prevChapter.words.size - 1).coerceAtLeast(0)
            )
        }

        else -> null
    }
}

private fun ReaderState.recalculateWpm(): ReaderState {
    val book = book ?: return copy(effectiveWpmInfo = null)
    return copy(effectiveWpmInfo = calculateEffectiveWpmInfo(book, position, settings))
}
