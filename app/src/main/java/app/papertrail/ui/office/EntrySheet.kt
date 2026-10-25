package app.papertrail.ui.office

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Clue
import app.papertrail.domain.DAY
import app.papertrail.domain.Kind
import app.papertrail.domain.Notice
import app.papertrail.domain.date
import app.papertrail.sound.Snd
import app.papertrail.ui.Home
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Chip
import app.papertrail.ui.components.Field
import app.papertrail.ui.components.Flow
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.LocalToast
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Sheet
import app.papertrail.ui.components.SuspectPicker
import app.papertrail.ui.theme.T

/** What a clue is, as the typewriter asks: spent, a fee, or cash taken out (a cold case until explained). */
private enum class What { SPEND, FEE, CASH }

/** The typewriter: a new clue, or one already on file to correct. */
@Composable
fun EntrySheet(h: Home, id: Long, fee: Boolean, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    val toast = LocalToast.current
    val old = h.s.clue(id)
    var what by remember { mutableStateOf(when { old?.kind == Kind.FEE || (old == null && fee) -> What.FEE; old?.cash == true -> What.CASH; else -> What.SPEND }) }
    var amount by remember { mutableStateOf(old?.let { w.amount(it.amount).replace(" ", "").replace(",", ".") }.orEmpty()) }
    var suspect by remember { mutableStateOf(old?.suspectId) }
    var place by remember { mutableStateOf(old?.place.orEmpty()) }
    var note by remember { mutableStateOf(old?.note.orEmpty()) }
    // Days back from today, for clues remembered late.
    var back by remember { mutableIntStateOf(0) }
    val minor = Notice.minor(amount.trim()) ?: 0

    Sheet(if (old == null) w.t("Новая улика", "New clue") else w.t("Улика", "Clue"), onClose, w.t("Закрыть", "Close")) {
        if (old == null || old.kind != Kind.VAULT) Flow {
            Chip(w.t("Трата", "Spent"), what == What.SPEND) { what = What.SPEND }
            Chip(w.t("Гонорар", "Fee in"), what == What.FEE) { what = What.FEE }
            Chip(w.t("Наличные", "Cash out"), what == What.CASH) { what = What.CASH }
        }
        Label(w.t("Сумма", "Amount"))
        Field(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' } }, "0", number = true, big = true)
        if (what == What.SPEND) {
            Label(w.t("Подозреваемый", "Suspect"))
            SuspectPicker(w, h.s.files, suspect, suggested = if (suspect == null) c.repo.suggest(h.s, place)?.id else null) { suspect = if (suspect == it.id) null else it.id }
        }
        if (what == What.CASH) Text(
            w.t("Снятые наличные становятся висяком, пока вы не вспомните, на что они ушли.", "Cash taken out is a cold case until you remember what it went on."),
            style = T.Small, modifier = Modifier.fillMaxWidth(),
        )
        Label(w.t("Где", "Where"))
        Field(place, { place = it }, w.t("лавка, кафе, сайт…", "shop, café, website…"), max = 40)
        Label(w.t("Пометка", "Note"))
        Field(note, { note = it }, w.t("если есть что сказать", "if there's anything to say"), max = 80)
        if (old == null) {
            Label(w.t("Когда", "When"))
            Flow {
                Chip(w.t("сегодня", "today"), back == 0) { back = 0 }
                Chip(w.t("вчера", "yesterday"), back == 1) { back = 1 }
                Chip(w.t("позавчера", "two days ago"), back == 2) { back = 2 }
            }
        } else Text(w.date(old.at.date(h.zone)) + ", " + w.time(old.at, h.zone), style = T.Small)
        Gap(18)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (old != null) Btn(w.t("Сжечь", "Burn"), Modifier.weight(1f), look = Look.GHOST) {
                c.write { repo.delete(old) }
                onClose()
            }
            Btn(w.t("В дело", "File it"), Modifier.weight(1f), enabled = minor > 0) {
                val kind = if (what == What.FEE) Kind.FEE else if (old?.kind == Kind.VAULT) Kind.VAULT else Kind.SPEND
                val clue = (old ?: Clue(amount = minor, at = System.currentTimeMillis() - back * DAY)).copy(
                    kind = kind, amount = minor, place = place.trim(), note = note.trim(), cash = what == What.CASH,
                    suspectId = if (what == What.SPEND) suspect else if (what == What.CASH && old?.solved == true) old.suspectId else null,
                )
                c.sfx.play(Snd.KEY); c.sfx.play(Snd.BELL, .5f)
                val plan = h.settings.plan
                c.write {
                    if (old == null) repo.add(clue) else repo.save(clue)
                    val opened = repo.investigate(plan)
                    opened.firstOrNull()?.let { toast.show(w.t("Открыто дело №${it.number}. Загляните на доску.", "Case no. ${it.number} is open. Take a look at the board.")) }
                }
                onClose()
            }
        }
        Gap(12)
    }
}
