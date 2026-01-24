package app.spread.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.spread.domain.TimingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_WPM = intPreferencesKey("base_wpm")
        val PERIOD_DELAY = intPreferencesKey("period_delay_ms")
        val COMMA_DELAY = intPreferencesKey("comma_delay_ms")
        val PARAGRAPH_DELAY = intPreferencesKey("paragraph_delay_ms")
        val LENGTH_TIMING_SCALE = floatPreferencesKey("length_timing_scale")
        val SPLIT_CHUNK_MULTIPLIER = floatPreferencesKey("split_chunk_multiplier")
        val ANCHOR_POSITION = floatPreferencesKey("anchor_position_percent")
        val VERTICAL_POSITION_PORTRAIT = floatPreferencesKey("vertical_position_portrait")
        val VERTICAL_POSITION_LANDSCAPE = floatPreferencesKey("vertical_position_landscape")
        val MAX_DISPLAY_CHARS = intPreferencesKey("max_display_chars")
    }

    val settings: Flow<TimingSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            TimingSettings(
                baseWpm = prefs[Keys.BASE_WPM] ?: TimingSettings.Default.baseWpm,
                periodDelayMs = prefs[Keys.PERIOD_DELAY] ?: TimingSettings.Default.periodDelayMs,
                commaDelayMs = prefs[Keys.COMMA_DELAY] ?: TimingSettings.Default.commaDelayMs,
                paragraphDelayMs = prefs[Keys.PARAGRAPH_DELAY] ?: TimingSettings.Default.paragraphDelayMs,
                lengthTimingScale = prefs[Keys.LENGTH_TIMING_SCALE] ?: TimingSettings.Default.lengthTimingScale,
                splitChunkMultiplier = prefs[Keys.SPLIT_CHUNK_MULTIPLIER] ?: TimingSettings.Default.splitChunkMultiplier,
                anchorPositionPercent = prefs[Keys.ANCHOR_POSITION] ?: TimingSettings.Default.anchorPositionPercent,
                verticalPositionPortrait = prefs[Keys.VERTICAL_POSITION_PORTRAIT] ?: TimingSettings.Default.verticalPositionPortrait,
                verticalPositionLandscape = prefs[Keys.VERTICAL_POSITION_LANDSCAPE] ?: TimingSettings.Default.verticalPositionLandscape,
                maxDisplayChars = prefs[Keys.MAX_DISPLAY_CHARS] ?: TimingSettings.Default.maxDisplayChars
            )
        }

    suspend fun saveSettings(settings: TimingSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_WPM] = settings.baseWpm
            prefs[Keys.PERIOD_DELAY] = settings.periodDelayMs
            prefs[Keys.COMMA_DELAY] = settings.commaDelayMs
            prefs[Keys.PARAGRAPH_DELAY] = settings.paragraphDelayMs
            prefs[Keys.LENGTH_TIMING_SCALE] = settings.lengthTimingScale
            prefs[Keys.SPLIT_CHUNK_MULTIPLIER] = settings.splitChunkMultiplier
            prefs[Keys.ANCHOR_POSITION] = settings.anchorPositionPercent
            prefs[Keys.VERTICAL_POSITION_PORTRAIT] = settings.verticalPositionPortrait
            prefs[Keys.VERTICAL_POSITION_LANDSCAPE] = settings.verticalPositionLandscape
            prefs[Keys.MAX_DISPLAY_CHARS] = settings.maxDisplayChars
        }
    }
}
