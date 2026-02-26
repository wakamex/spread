package app.spread.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spread.domain.*
import kotlin.math.roundToInt

// Orangered - optimal for RSVP ORP highlighting on dark backgrounds
// Better perceived luminance and edge contrast than pure red
private val Orangered = Color(0xFFFF4500)

@Composable
fun ReaderScreen(
    state: ReaderState,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onWpmChange: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenBook: () -> Unit = {},
    onRestart: () -> Unit = {},
    onSkipWords: (Int) -> Unit = {},
    onPrevChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onJumpToChapter: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val word = state.currentWord?.text ?: ""
    val chapter = state.currentChapter
    val effectiveWpm = state.effectiveWpmInfo
    var showToc by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Zen mode: animate UI alpha instead of removing from layout (prevents word position shift)
    val uiAlpha by animateFloatAsState(
        targetValue = if (state.playing) 0f else 1f,
        label = "zenModeAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header - fades in Zen mode (during playback) but stays in layout
        TopBar(
            chapterTitle = chapter?.title ?: "",
            onOpenBook = onOpenBook,
            onSettingsClick = onSettingsClick,
            onTocClick = { if (state.book != null) showToc = true },
            alpha = uiAlpha
        )

        // Table of Contents bottom sheet
        if (showToc && state.book != null) {
            TocBottomSheet(
                chapters = state.book.chapters,
                currentChapterIndex = state.position.chapterIndex,
                effectiveWpmInfo = effectiveWpm,
                onDismiss = { showToc = false },
                onChapterSelected = { index ->
                    onJumpToChapter(index)
                    showToc = false
                }
            )
        }

        // Word display area
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val verticalPosition = if (isLandscape) {
            state.settings.verticalPositionLandscape
        } else {
            state.settings.verticalPositionPortrait
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val verticalOffsetDp = maxHeight * verticalPosition

            when {
                state.book == null -> {
                    Text(
                        text = "No book loaded",
                        color = Color.Gray,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.isAtEnd && !state.playing -> {
                    // Book finished - show restart option
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Finished!",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onRestart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Restart", fontSize = 18.sp)
                        }
                    }
                }
                else -> {
                    WordDisplay(
                        word = word,
                        anchorPositionPercent = state.settings.anchorPositionPercent,
                        maxDisplayChars = state.settings.maxDisplayChars,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = verticalOffsetDp)
                    )
                }
            }
        }

        // Bottom controls - fades in Zen mode (during playback) but stays in layout
        BottomBar(
            playing = state.playing,
            effectiveWpm = effectiveWpm,
            progress = state.progress,
            baseWpm = state.settings.baseWpm,
            onToggle = onToggle,
            onSeek = onSeek,
            onWpmChange = onWpmChange,
            onSkipWords = onSkipWords,
            onPrevChapter = onPrevChapter,
            onNextChapter = onNextChapter,
            onInfoRowClick = { showJumpDialog = true },
            alpha = uiAlpha
        )
    }

    if (showJumpDialog) {
        JumpToDialog(
            totalChapterMinutes = effectiveWpm?.chapter?.totalMinutes ?: 0.0,
            onSeek = onSeek,
            onDismiss = { showJumpDialog = false }
        )
    }
}

@Composable
private fun TopBar(
    chapterTitle: String,
    onOpenBook: () -> Unit,
    onSettingsClick: () -> Unit,
    onTocClick: () -> Unit,
    alpha: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTocClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapterTitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (chapterTitle.isNotEmpty()) {
                Text(
                    text = " ▼",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }

        Row {
            IconButton(onClick = onOpenBook) {
                Text("📖", fontSize = 18.sp)
            }
            IconButton(onClick = onSettingsClick) {
                Text("⚙", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

/**
 * Calculate optimal font size to fit maxDisplayChars on screen.
 * Uses runtime font measurement for accurate sizing across all devices.
 */
@Composable
private fun rememberOptimalFontSize(maxDisplayChars: Int, anchorPosition: Float): androidx.compose.ui.unit.TextUnit {
    val configuration = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(configuration.screenWidthDp, configuration.orientation, maxDisplayChars, anchorPosition) {
        // Measure actual character width at base font size
        val testStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = FontSizing.BASE_FONT_SP.sp
        )
        val measuredWidthPx = textMeasurer.measure("M", testStyle).size.width
        val measuredCharWidthDp = with(density) { measuredWidthPx.toDp().value }

        // Calculate font size using actual measured width
        FontSizing.calculateFontSpFromMeasured(
            screenWidthDp = configuration.screenWidthDp.toFloat(),
            measuredCharWidthDp = measuredCharWidthDp,
            maxDisplayChars = maxDisplayChars,
            anchorPosition = anchorPosition
        ).sp
    }
}

@Composable
private fun WordDisplay(
    word: String,
    anchorPositionPercent: Float,
    maxDisplayChars: Int,
    modifier: Modifier = Modifier
) {
    if (word.isEmpty()) {
        return
    }

    val orpIndex = calculateORP(word)
    // Dynamic font size based on screen width, max chars, and anchor position
    val fontSize = rememberOptimalFontSize(maxDisplayChars, anchorPositionPercent)
    val guideLineColor = Color.White.copy(alpha = 0.08f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper guide line
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(1.dp)
                .background(guideLineColor)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Word with ORP positioned at anchorPositionPercent of screen width
        // Default 0.42 (42%) places anchor left of center to accommodate
        // the asymmetric ORP (35% into word = more chars extend right)
        // Horizontal padding must match EDGE_PADDING_DP assumed by FontSizing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FontSizing.EDGE_PADDING_DP.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.layout { measurable, constraints ->
                    // Measure the row without width constraints (unbounded)
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = Constraints.Infinity)
                    )

                    // Calculate exact character width from measured total
                    val charWidth = placeable.width.toFloat() / word.length

                    // The exact center-point of the ORP letter relative to Row's start
                    val orpCenterInRow = (orpIndex * charWidth) + (charWidth / 2f)

                    // Calculate anchor X position based on anchorPositionPercent
                    val anchorX = constraints.maxWidth * anchorPositionPercent

                    // Place word so ORP aligns with anchor position
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(
                            x = (anchorX - orpCenterInRow).roundToInt(),
                            y = 0
                        )
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                word.forEachIndexed { index, char ->
                    Text(
                        text = char.toString(),
                        color = if (index == orpIndex) Orangered else Color.White,
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == orpIndex) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lower guide line
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(1.dp)
                .background(guideLineColor)
        )
    }
}

/**
 * Calculate Optimal Recognition Point (ORP) - the letter the eye focuses on.
 * Research shows ORP is approximately 35% into the word.
 */
private fun calculateORP(word: String): Int {
    if (word.isEmpty()) return 0
    return (word.length * 0.35f).toInt().coerceAtLeast(0)
}

@Composable
internal fun BottomBar(
    playing: Boolean,
    effectiveWpm: EffectiveWpmInfo?,
    progress: Progress,
    baseWpm: Int,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onWpmChange: (Int) -> Unit,
    onSkipWords: (Int) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onInfoRowClick: () -> Unit,
    alpha: Float = 1f
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        // Effective WPM and time remaining
        effectiveWpm?.let { info ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (playing) "▶" else "⏸"} ${info.chapter.wpm} effective WPM",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTime(info.chapter.minutesRemaining),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = onInfoRowClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Jump", fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chapter progress (draggable slider)
        Slider(
            value = progress.chapter,
            onValueChange = { onSeek(it) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        // Book progress (read-only)
        LinearProgressIndicator(
            progress = progress.book,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Color.White.copy(alpha = 0.5f),
            trackColor = Color.White.copy(alpha = 0.05f),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevChapter) {
                Text("⏮", fontSize = 24.sp, color = Color.White)
            }
            IconButton(onClick = { onSkipWords(-10) }) {
                Text("⏪", fontSize = 24.sp, color = Color.White)
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    text = if (playing) "⏸" else "▶",
                    fontSize = 32.sp,
                    color = Color.White
                )
            }
            IconButton(onClick = { onSkipWords(10) }) {
                Text("⏩", fontSize = 24.sp, color = Color.White)
            }
            IconButton(onClick = onNextChapter) {
                Text("⏭", fontSize = 24.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WPM slider with centered label
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$baseWpm WPM",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = baseWpm.toFloat(),
                onValueChange = { onWpmChange(it.roundToInt()) },
                valueRange = 100f..1000f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun JumpToDialog(
    totalChapterMinutes: Double,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("Percentage") }
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Jump to position", color = Color.White) },
        text = {
            JumpToDialogBody(
                mode = mode,
                input = input,
                onModeChange = { mode = it; input = "" },
                onInputChange = { input = it }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = input.toDoubleOrNull() ?: return@TextButton
                    val fraction = when (mode) {
                        "Percentage" -> (value / 100.0).coerceIn(0.0, 1.0)
                        "Time" -> if (totalChapterMinutes > 0) {
                            (value / totalChapterMinutes).coerceIn(0.0, 1.0)
                        } else 0.0
                        else -> return@TextButton
                    }
                    onSeek(fraction.toFloat())
                    onDismiss()
                }
            ) {
                Text("Go", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
internal fun JumpToDialogBody(
    mode: String = "Percentage",
    input: String = "",
    onModeChange: (String) -> Unit = {},
    onInputChange: (String) -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.selectable(
                    selected = mode == "Percentage",
                    onClick = { onModeChange("Percentage") }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == "Percentage",
                    onClick = { onModeChange("Percentage") },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color.White,
                        unselectedColor = Color.White.copy(alpha = 0.6f)
                    )
                )
                Text("Percentage", color = Color.White, fontSize = 14.sp)
            }
            Row(
                modifier = Modifier.selectable(
                    selected = mode == "Time",
                    onClick = { onModeChange("Time") }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = mode == "Time",
                    onClick = { onModeChange("Time") },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color.White,
                        unselectedColor = Color.White.copy(alpha = 0.6f)
                    )
                )
                Text("Time (min)", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = {
                Text(
                    if (mode == "Percentage") "Percentage (0–100)" else "Minutes",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTime(minutes: Double): String {
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "${minutes.roundToInt()} min"
        else -> {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).roundToInt()
            "${hours}h ${mins}m"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocBottomSheet(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    effectiveWpmInfo: EffectiveWpmInfo?,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Table of Contents",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Divider(color = Color.White.copy(alpha = 0.2f))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val isCurrent = index == currentChapterIndex
                    val chapterTime = effectiveWpmInfo?.let {
                        val wordsInChapter = chapter.stats.wordCount
                        val wpm = it.chapter.wpm
                        if (wpm > 0) wordsInChapter.toDouble() / wpm else 0.0
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterSelected(index) }
                            .background(if (isCurrent) Orangered.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCurrent) {
                                Text(
                                    text = "▶ ",
                                    color = Orangered,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "${index + 1}. ${chapter.title}",
                                color = if (isCurrent) Orangered else Color.White,
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }

                        chapterTime?.let {
                            Text(
                                text = formatTime(it),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
