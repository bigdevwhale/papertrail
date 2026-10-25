package app.papertrail.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.papertrail.BuildConfig
import app.papertrail.container
import app.papertrail.data.Settings
import app.papertrail.domain.Gender
import app.papertrail.domain.Notice
import app.papertrail.notify.Informant
import app.papertrail.ui.Home
import app.papertrail.ui.Words
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Chip
import app.papertrail.ui.components.CommunityLinks
import app.papertrail.ui.components.Field
import app.papertrail.ui.components.Flow
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Sheet
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

val CURRENCIES = listOf("₽" to false, "$" to true, "€" to true, "£" to true, "₸" to false, "₴" to false, "Br" to false, "zł" to false, "₾" to false, "֏" to false)

@Composable
fun SettingsSheet(h: Home, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    val st = h.settings
    fun set(f: (Settings) -> Settings) = c.write { settings.update(f) }
    Sheet(w.t("Настройки", "Settings"), onClose, w.t("Закрыть", "Close")) {
        Advance(w, st) { a, d -> set { it.copy(advance = a, payday = d) } }
        Label(w.t("Валюта", "Currency"))
        Flow { for ((sym, first) in CURRENCIES) Chip(sym, st.currency == sym) { set { it.copy(currency = sym, currencyFirst = first) } } }
        if (w.ru) {
            Label("Обращение в рапортах")
            Flow {
                Chip("сыщик — «устал»", st.gender == Gender.HE) { set { it.copy(gender = Gender.HE) } }
                Chip("сыщица — «устала»", st.gender == Gender.SHE) { set { it.copy(gender = Gender.SHE) } }
            }
        }
        Language(w)
        Informants(w, st) { set(it) }
        Label(w.t("Контора", "The office"))
        Toggle(w.t("Вечерний рапорт в 21:00", "Evening report at 9 pm"), st.report) { on -> set { it.copy(report = on) } }
        Toggle(w.t("Звуки: дождь, машинка, штамп", "Sounds: rain, typewriter, stamp"), st.sound) { on -> set { it.copy(sound = on) } }
        Gap(14)
        CommunityLinks(w)
        Gap(12)
        Text(
            w.t("Висяк ${BuildConfig.VERSION_NAME}. Все улики хранятся только на этом телефоне.", "Paper Trail ${BuildConfig.VERSION_NAME}. Every clue stays on this phone."),
            style = T.Small,
        )
        Gap(16)
    }
}

/** The advance and the payday it's counted from. */
@Composable
fun Advance(w: Words, st: Settings, onSet: (Long, Int) -> Unit) {
    var a by remember { mutableStateOf(if (st.advance > 0) (st.advance / 100).toString() else "") }
    var d by remember { mutableStateOf(st.payday.toString()) }
    val am = Notice.minor(a) ?: 0
    val dd = d.toIntOrNull()?.coerceIn(1, 31) ?: 0
    Label(w.t("Аванс на месяц", "Monthly advance"))
    Text(w.t("Сколько можно потратить за месяц на жизнь, вместе со счетами. Не весь доход — только то, что вы готовы отпустить в город.", "How much may be spent in a month on living, bills included. Not all your income, only what you're ready to let loose in town."), style = T.Small)
    Gap(6)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(a, { a = it.filter { ch -> ch.isDigit() } }, w.t("сумма", "amount"), Modifier.weight(2f), number = true)
        Field(d, { d = it.filter { ch -> ch.isDigit() }.take(2) }, w.t("день", "day"), Modifier.weight(1f), number = true)
        Btn("OK", look = Look.PAPER, enabled = am > 0 && dd > 0 && (am != st.advance || dd != st.payday)) { onSet(am, dd) }
    }
    Text(w.t("Второе поле — день зарплаты: с него начинается период.", "The second box is payday: each period starts on it."), style = T.Small, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Language(w: Words) {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    Label(w.t("Язык", "Language"))
    Flow {
        Chip(w.t("как в системе", "system"), tags.isEmpty()) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        Chip("Русский", tags.startsWith("ru")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
        Chip("English", tags.startsWith("en")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
    }
}

/** Notification access, and which apps are banks. */
@Composable
fun Informants(w: Words, st: Settings, set: ((Settings) -> Settings) -> Unit) {
    val ctx = LocalContext.current
    val granted = Informant.granted(ctx)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Label(w.t("Осведомитель", "The informant"))
    Text(
        w.t("Читает уведомления банков на этом телефоне и кладёт на стол конверты с суммой и местом. Слушает только приложения, отмеченные как банк. Ничего никуда не отправляет.",
            "Reads bank notifications on this phone and leaves envelopes with the sum and the place on the desk. Only listens to apps marked as banks. Sends nothing anywhere."),
        style = T.Small,
    )
    Gap(8)
    if (!granted) Btn(w.t("Дать доступ к уведомлениям", "Allow notification access"), Modifier.fillMaxWidth(), look = Look.PAPER) {
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else Text(w.t("Доступ есть.", "Access granted."), style = T.Small.copy(color = C.fog))
    val apps = (st.banks + st.heard).sorted()
    if (apps.isEmpty()) {
        if (granted) Text(w.t("Банки появятся здесь, как только от них придёт уведомление о деньгах.", "Banks show up here as soon as one sends a money notification."), style = T.Small, modifier = Modifier.padding(top = 6.dp))
        return
    }
    Gap(6)
    for (pkg in apps) Toggle(label(ctx, pkg), pkg in st.banks) { on ->
        set { if (on) it.copy(banks = it.banks + pkg, heard = it.heard - pkg) else it.copy(banks = it.banks - pkg, heard = it.heard + pkg) }
    }
}

private fun label(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.Body, modifier = Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.paperHi, uncheckedTrackColor = C.desk, uncheckedThumbColor = C.smoke, uncheckedBorderColor = C.line))
    }
}

