package app.spread.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spread.domain.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: TimingSettings,
    effectiveWpmInfo: EffectiveWpmInfo?,
    onDismiss: () -> Unit,
    onSettingsChange: (Action) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Reading Settings",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Effective WPM display - prominent position
            effectiveWpmInfo?.let { info ->
                EffectiveWpmDisplay(info = info, baseWpm = settings.baseWpm)
                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Base WPM
            SettingSlider(
                label = "Base Speed",
                value = settings.baseWpm.toFloat(),
                valueRange = 100f..1000f,
                valueLabel = "${settings.baseWpm} WPM",
                onValueChange = { onSettingsChange(Action.SetBaseWpm(it.roundToInt())) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Presets
            Text(
                text = "Presets",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetButton(
                    label = "Uniform",
                    selected = settings.isUniform,
                    onClick = { onSettingsChange(Action.ApplyPreset(TimingSettings.Uniform.copy(baseWpm = settings.baseWpm))) },
                    modifier = Modifier.weight(1f)
                )
                PresetButton(
                    label = "Natural",
                    selected = settings.isNatural,
                    onClick = { onSettingsChange(Action.ApplyPreset(TimingSettings.Natural.copy(baseWpm = settings.baseWpm))) },
                    modifier = Modifier.weight(1f)
                )
                PresetButton(
                    label = "Deep",
                    selected = settings.isComprehension,
                    onClick = { onSettingsChange(Action.ApplyPreset(TimingSettings.Comprehension.copy(baseWpm = settings.baseWpm))) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Display settings
            Text(
                text = "Display",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingSlider(
                label = "Max Chunk Size",
                value = settings.maxDisplayChars.toFloat(),
                valueRange = 10f..24f,
                valueLabel = "${settings.maxDisplayChars} chars",
                onValueChange = { onSettingsChange(Action.SetMaxDisplayChars(it.roundToInt())) }
            )

            Text(
                text = "Larger = fewer word splits, smaller font",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Punctuation delays
            Text(
                text = "Punctuation Pauses",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingSlider(
                label = "Period (. ! ?)",
                value = settings.periodDelayMs.toFloat(),
                valueRange = 0f..500f,
                valueLabel = "${settings.periodDelayMs}ms",
                onValueChange = { onSettingsChange(Action.SetPeriodDelay(it.roundToInt())) }
            )

            SettingSlider(
                label = "Comma (, ; :)",
                value = settings.commaDelayMs.toFloat(),
                valueRange = 0f..300f,
                valueLabel = "${settings.commaDelayMs}ms",
                onValueChange = { onSettingsChange(Action.SetCommaDelay(it.roundToInt())) }
            )

            SettingSlider(
                label = "Paragraph",
                value = settings.paragraphDelayMs.toFloat(),
                valueRange = 0f..1000f,
                valueLabel = "${settings.paragraphDelayMs}ms",
                onValueChange = { onSettingsChange(Action.SetParagraphDelay(it.roundToInt())) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Word length delays
            Text(
                text = "Long Word Delays",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingSlider(
                label = "Medium (5-8 chars)",
                value = settings.mediumWordExtraMs.toFloat(),
                valueRange = 0f..100f,
                valueLabel = "+${settings.mediumWordExtraMs}ms",
                onValueChange = { onSettingsChange(Action.SetMediumWordExtra(it.roundToInt())) }
            )

            SettingSlider(
                label = "Long (9-12 chars)",
                value = settings.longWordExtraMs.toFloat(),
                valueRange = 0f..150f,
                valueLabel = "+${settings.longWordExtraMs}ms",
                onValueChange = { onSettingsChange(Action.SetLongWordExtra(it.roundToInt())) }
            )

            SettingSlider(
                label = "Very Long (13+)",
                value = settings.veryLongWordExtraMs.toFloat(),
                valueRange = 0f..200f,
                valueLabel = "+${settings.veryLongWordExtraMs}ms",
                onValueChange = { onSettingsChange(Action.SetVeryLongWordExtra(it.roundToInt())) }
            )

        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )
            Text(
                text = valueLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun EffectiveWpmDisplay(info: EffectiveWpmInfo, baseWpm: Int) {
    Column {
        Text(
            text = "Effective Reading Speed",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Chapter", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Text(
                    "${info.chapter.wpm} WPM",
                    color = Color.White,
                    fontSize = 18.sp
                )
                val chapterDiff = ((info.chapter.wpm - baseWpm).toFloat() / baseWpm * 100).roundToInt()
                Text(
                    "${if (chapterDiff >= 0) "+" else ""}$chapterDiff%",
                    color = if (chapterDiff < 0) Color(0xFFFF6B6B) else Color(0xFF6BCB77),
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Book", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Text(
                    "${info.book.wpm} WPM",
                    color = Color.White,
                    fontSize = 18.sp
                )
                Text(
                    formatDuration(info.book.minutesRemaining),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatDuration(minutes: Double): String {
    return when {
        minutes < 1 -> "< 1 min left"
        minutes < 60 -> "${minutes.roundToInt()} min left"
        else -> {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).roundToInt()
            "${hours}h ${mins}m left"
        }
    }
}

// Extension properties for preset detection
private val TimingSettings.isUniform: Boolean
    get() = periodDelayMs == 0 && commaDelayMs == 0 && paragraphDelayMs == 0 &&
            mediumWordExtraMs == 0 && longWordExtraMs == 0 && veryLongWordExtraMs == 0

private val TimingSettings.isNatural: Boolean
    get() = periodDelayMs == 150 && commaDelayMs == 75 && paragraphDelayMs == 300 &&
            mediumWordExtraMs == 20 && longWordExtraMs == 40 && veryLongWordExtraMs == 60

private val TimingSettings.isComprehension: Boolean
    get() = periodDelayMs == 300 && commaDelayMs == 150 && paragraphDelayMs == 500 &&
            mediumWordExtraMs == 30 && longWordExtraMs == 60 && veryLongWordExtraMs == 100
