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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
            onSettingsClick = onSettingsClick
        )

        // Word display - positioned in upper third for ergonomic focus
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.book != null) {
                WordDisplay(
                    word = word,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp) // Upper third positioning
                )
            } else {
                Text(
                    text = "No book loaded",
                    color = Color.Gray,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
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

        IconButton(onClick = onSettingsClick) {
            Text("⚙", fontSize = 20.sp, color = Color.White)
        }
    }
}

@Composable
private fun WordDisplay(
    word: String,
    modifier: Modifier = Modifier
) {
    if (word.isEmpty()) {
        return
    }

    val orpIndex = calculateORP(word)
    val fontSize = 48.sp
    val guideLineColor = Color.White.copy(alpha = 0.08f)

    // With monospace font, all chars have equal width
    // Calculate offset to center the ORP character at screen center
    // Offset = (orpIndex - wordLength/2) * charWidth
    // Negative offset means shift right, positive means shift left
    val charCount = word.length
    val offsetChars = orpIndex - (charCount - 1) / 2f

    // Approximate character width for monospace at 48sp
    val density = LocalDensity.current
    val charWidthDp = with(density) { (fontSize.toPx() * 0.6f).toDp() }
    val offsetDp = charWidthDp * offsetChars

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

        // Word with ORP center-locked
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.offset(x = -offsetDp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                word.forEachIndexed { index, char ->
                    Text(
                        text = char.toString(),
                        color = if (index == orpIndex) Orangered else Color.White,
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == orpIndex) FontWeight.Bold else FontWeight.Normal
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

        // Chapter progress
        LinearProgressIndicator(
            progress = progress.chapter,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Book progress
        LinearProgressIndicator(
            progress = progress.book,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Color.White.copy(alpha = 0.5f),
            trackColor = Color.White.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // WPM slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$baseWpm WPM",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.width(70.dp)
            )

            Slider(
                value = baseWpm.toFloat(),
                onValueChange = { onWpmChange(it.roundToInt()) },
                valueRange = 100f..1000f,
                modifier = Modifier.weight(1f),
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
