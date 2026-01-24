package app.spread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onWpmChange: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenBook: () -> Unit = {},
    onRestart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val word = state.currentWord?.text ?: ""
    val chapter = state.currentChapter
    val effectiveWpm = state.effectiveWpmInfo

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
        // Header
        TopBar(
            chapterTitle = chapter?.title ?: "",
            onOpenBook = onOpenBook,
            onSettingsClick = onSettingsClick
        )

        // Word display - vertical position adapts to orientation for ergonomics
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
            val verticalOffsetDp = (maxHeight * verticalPosition)

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

        // Bottom controls
        BottomBar(
            playing = state.playing,
            effectiveWpm = effectiveWpm,
            progress = state.progress,
            baseWpm = state.settings.baseWpm,
            onSeek = onSeek,
            onWpmChange = onWpmChange
        )
    }
}

@Composable
private fun TopBar(
    chapterTitle: String,
    onOpenBook: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterTitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

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
 * Typically around 30% into the word.
 */
private fun calculateORP(word: String): Int {
    val len = word.length
    return when {
        len <= 1 -> 0
        len <= 5 -> 1
        len <= 9 -> 2
        len <= 13 -> 3
        else -> 4
    }
}

@Composable
private fun BottomBar(
    playing: Boolean,
    effectiveWpm: EffectiveWpmInfo?,
    progress: Progress,
    baseWpm: Int,
    onSeek: (Float) -> Unit,
    onWpmChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Effective WPM and time remaining
        effectiveWpm?.let { info ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${if (playing) "▶" else "⏸"} ${info.chapter.wpm} effective WPM",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
                Text(
                    text = formatTime(info.chapter.minutesRemaining),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chapter progress (rounded ends to match slider thumb)
        LinearProgressIndicator(
            progress = progress.chapter,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Book progress
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
