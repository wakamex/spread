package app.spread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Orangered - optimal for RSVP ORP highlighting on dark backgrounds
private val Orangered = Color(0xFFFF4500)

/**
 * Testable word display component for screenshot testing.
 * Shows the word with ORP center-locked and guide lines.
 */
@Composable
fun WordDisplayTestable(
    word: String,
    modifier: Modifier = Modifier,
    showCenterLine: Boolean = false,  // Debug: show vertical center line
    showBoundaries: Boolean = false   // Debug: show screen edge boundaries
) {
    if (word.isEmpty()) return

    val orpIndex = calculateORPTestable(word)
    // Fixed font size - word splitting in Rust parser handles long words
    val fontSize = 48.sp
    val guideLineColor = Color.White.copy(alpha = 0.08f)
    val boundaryColor = Color.Yellow.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 32.dp)
    ) {
        // Left boundary marker
        if (showBoundaries) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(100.dp)
                    .background(boundaryColor)
            )
        }
        // Right boundary marker
        if (showBoundaries) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(100.dp)
                    .background(boundaryColor)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                // Debug center line
                if (showCenterLine) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(80.dp)
                            .background(Color.Red.copy(alpha = 0.5f))
                    )
                }

                Row(
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = Constraints.Infinity)
                        )
                        val charWidth = placeable.width.toFloat() / word.length
                        val orpCenterInRow = (orpIndex * charWidth) + (charWidth / 2f)

                        // Use zero width so Box centers at our "anchor point"
                        // Then place the Row offset so ORP aligns with that anchor
                        layout(0, placeable.height) {
                            placeable.placeRelative(
                                x = (-orpCenterInRow).roundToInt(),
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

            // Word info for debugging
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"$word\" (${word.length} chars, ORP: $orpIndex)",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Calculate Optimal Recognition Point (ORP) - the letter the eye focuses on.
 */
fun calculateORPTestable(word: String): Int {
    val len = word.length
    return when {
        len <= 1 -> 0
        len <= 5 -> 1
        len <= 9 -> 2
        len <= 13 -> 3
        else -> 4
    }
}
