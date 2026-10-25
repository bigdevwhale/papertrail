package app.papertrail.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.papertrail.domain.Gender
import app.papertrail.domain.Plan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val onboarded: Boolean = false,
    /** How Russian words address the detective: «устал» or «устала». */
    val gender: Gender? = null,
    /** The monthly advance in minor units, counted from [payday]. */
    val advance: Long = 0,
    val payday: Int = 1,
    /** The money sign and whether it goes before the number ($12) or after (12 ₽). */
    val currency: String = "₽",
    val currencyFirst: Boolean = false,
    /** Apps whose notifications are read as bank messages. */
    val banks: Set<String> = emptySet(),
    /** Apps that sent something money-like but aren't banks yet; offered in settings. */
    val heard: Set<String> = emptySet(),
    val report: Boolean = true,
    val sound: Boolean = true,
) {
    val plan get() = Plan(advance, payday)
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        gender = Gender.entries.firstOrNull { it.name == this[GENDER] },
        advance = this[ADVANCE] ?: 0,
        payday = this[PAYDAY] ?: 1,
        currency = this[CURRENCY] ?: "₽",
        currencyFirst = this[CURRENCY_FIRST] ?: false,
        banks = this[BANKS] ?: emptySet(),
        heard = this[HEARD] ?: emptySet(),
        report = this[REPORT] ?: true,
        sound = this[SOUND] ?: true,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[GENDER] = s.gender?.name ?: ""
            it[ADVANCE] = s.advance
            it[PAYDAY] = s.payday
            it[CURRENCY] = s.currency
            it[CURRENCY_FIRST] = s.currencyFirst
            it[BANKS] = s.banks
            it[HEARD] = s.heard
            it[REPORT] = s.report
            it[SOUND] = s.sound
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GENDER = stringPreferencesKey("gender")
        val ADVANCE = longPreferencesKey("advance")
        val PAYDAY = intPreferencesKey("payday")
        val CURRENCY = stringPreferencesKey("currency")
        val CURRENCY_FIRST = booleanPreferencesKey("currencyFirst")
        val BANKS = stringSetPreferencesKey("banks")
        val HEARD = stringSetPreferencesKey("heard")
        val REPORT = booleanPreferencesKey("report")
        val SOUND = booleanPreferencesKey("sound")
    }
}
