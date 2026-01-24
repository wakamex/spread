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
        val MEDIUM_WORD_EXTRA = intPreferencesKey("medium_word_extra_ms")
        val LONG_WORD_EXTRA = intPreferencesKey("long_word_extra_ms")
        val VERY_LONG_WORD_EXTRA = intPreferencesKey("very_long_word_extra_ms")
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
                mediumWordExtraMs = prefs[Keys.MEDIUM_WORD_EXTRA] ?: TimingSettings.Default.mediumWordExtraMs,
                longWordExtraMs = prefs[Keys.LONG_WORD_EXTRA] ?: TimingSettings.Default.longWordExtraMs,
                veryLongWordExtraMs = prefs[Keys.VERY_LONG_WORD_EXTRA] ?: TimingSettings.Default.veryLongWordExtraMs
            )
        }

    suspend fun saveSettings(settings: TimingSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_WPM] = settings.baseWpm
            prefs[Keys.PERIOD_DELAY] = settings.periodDelayMs
            prefs[Keys.COMMA_DELAY] = settings.commaDelayMs
            prefs[Keys.PARAGRAPH_DELAY] = settings.paragraphDelayMs
            prefs[Keys.MEDIUM_WORD_EXTRA] = settings.mediumWordExtraMs
            prefs[Keys.LONG_WORD_EXTRA] = settings.longWordExtraMs
            prefs[Keys.VERY_LONG_WORD_EXTRA] = settings.veryLongWordExtraMs
        }
    }
}
