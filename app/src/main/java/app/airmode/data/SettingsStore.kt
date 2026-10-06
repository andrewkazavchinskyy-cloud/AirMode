package app.airmode.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.airmode.domain.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.airModeSettings by preferencesDataStore("airmode")
data class Settings(
    val popup: Boolean = true, val persistent: Boolean = false, val autoStart: Boolean = true,
    val tileModes: Set<Mode> = Mode.entries.toSet(), val language: String = "ru", val onboarded: Boolean = false,
)
class SettingsStore(context: Context, scope: CoroutineScope) {
    private val data = context.applicationContext.airModeSettings
    private val popup = booleanPreferencesKey("popup")
    private val persistent = booleanPreferencesKey("persistent")
    private val auto = booleanPreferencesKey("auto_start")
    private val modes = stringSetPreferencesKey("tile_modes")
    private val language = stringPreferencesKey("language")
    private val onboarding = booleanPreferencesKey("onboarded")
    val state = data.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        val selected = p[modes]?.mapNotNull { name -> Mode.entries.firstOrNull { it.name == name } }?.toSet()
        Settings(p[popup] ?: true, p[persistent] ?: false, p[auto] ?: true,
            selected?.takeIf { it.size >= 2 } ?: Mode.entries.toSet(), p[language] ?: "ru", p[onboarding] ?: false)
    }.stateIn(scope, SharingStarted.Eagerly, Settings())
    suspend fun setPopup(value: Boolean) { data.edit { it[popup] = value } }
    suspend fun setPersistent(value: Boolean) { data.edit { it[persistent] = value } }
    suspend fun setAutoStart(value: Boolean) { data.edit { it[auto] = value } }
    suspend fun setLanguage(value: String) { require(value in setOf("system", "ru", "en")); data.edit { it[language] = value } }
    suspend fun setTileModes(value: Set<Mode>) { require(value.size >= 2); data.edit { it[modes] = value.map { m -> m.name }.toSet() } }
    suspend fun completeOnboarding() { data.edit { it[onboarding] = true } }
}
