package app.papertrail.ui.files

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Kind
import app.papertrail.domain.Ledger
import app.papertrail.domain.Notice
import app.papertrail.domain.Period
import app.papertrail.domain.Role
import app.papertrail.domain.date
import app.papertrail.domain.hour
import app.papertrail.ui.Home
import app.papertrail.ui.Nav
import app.papertrail.ui.Overlay
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Chip
import app.papertrail.ui.components.Field
import app.papertrail.ui.components.Flow
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Note
import app.papertrail.ui.components.Portrait
import app.papertrail.ui.components.Sheet
import app.papertrail.ui.office.ClueRow
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

/** The file on one suspect: the photo, the way they work, their record month by month, and the tail. */
@Composable
fun DossierSheet(h: Home, id: Long, nav: Nav, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    val s = h.s.suspect(id) ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val mine = h.s.clues.filter { it.suspectId == id && it.kind == Kind.SPEND }
    val periods = generateSequence(h.today.period) { it.prev(h.settings.payday) }.take(6).toList().reversed()
    val sums = periods.map { p -> Ledger.inPeriod(mine, p, h.zone).sumOf { it.amount } }

    Sheet(w.t("Досье", "File"), onClose, w.t("Закрыть", "Close")) {
        Row {
            Portrait(s, Modifier.width(118.dp).rotate(-1.5f))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(w.alias(s), style = T.Heading)
                Text(w.name(s), style = T.Body.copy(color = C.fog))
                Text(w.role(s.role), style = T.Small)
                Gap(8)
                Text(w.t("Почерк: ", "Way of working: ") + w.mo(s), style = T.Italic.copy(color = C.fog))
            }
        }
        Label(w.t("Этот период", "This period"))
        Row {
            Stat(w.money(sums.last()), w.t("сейчас", "now"), Modifier.weight(1f))
            Stat(w.money(sums.getOrElse(sums.size - 2) { 0 }), w.t("прошлый", "last"), Modifier.weight(1f))
            Stat(if (s.limit > 0) w.money(s.limit) else "—", w.t("наружка", "tail"), Modifier.weight(1f))
        }
        Label(w.t("Судимости по месяцам", "Record, month by month"))
        Note { Bars(periods, sums, s.limit, h) }
        if (mine.size >= 3) {
            Label(w.t("Почерк по часам и дням", "By hour and by day"))
            Note {
                Hours(mine.map { it.at.hour(h.zone) })
                Gap(10)
                Days(mine.map { it.at.date(h.zone).dayOfWeek.value }, h)
            }
        }
        Tail(h, s.limit) { lim -> c.write { repo.saveSuspect(s.copy(limit = lim)) } }
        val recent = mine.sortedByDescending { it.at }.take(15)
        if (recent.isNotEmpty()) {
            Label(w.t("Последние улики", "Latest clues"))
            Note {
                for ((i, cl) in recent.withIndex()) {
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.paperDim))
                    ClueRow(h, cl) { nav.push(Overlay.Entry(cl.id)) }
                }
            }
        }
        Edit(h, s.name.ifBlank { w.name(s) }, s.alias.ifBlank { w.alias(s) }, s.role, onSave = { n, a, r -> c.write { repo.saveSuspect(s.copy(name = n, alias = a, role = r)) } }, onHide = {
            c.write { repo.saveSuspect(s.copy(hidden = true)) }; onClose()
        })
        Gap(16)
    }
}

@Composable
private fun Stat(big: String, sub: String, modifier: Modifier) {
    Column(modifier) {
        Text(big, style = T.Type.copy(color = C.paper))
        Text(sub.uppercase(), style = T.Label)
    }
}

/** Six periods as bars, the tail as a red line across. */
@Composable
private fun Bars(periods: List<Period>, sums: List<Long>, limit: Long, h: Home) {
    val top = maxOf(sums.maxOrNull() ?: 0, limit, 1)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val bw = size.width / periods.size
        for ((i, v) in sums.withIndex()) {
            val bh = size.height * v / top
            drawRect(if (i == sums.lastIndex) C.ink else C.inkSoft, Offset(i * bw + bw * .2f, size.height - bh), Size(bw * .6f, bh))
        }
        if (limit > 0) {
            val y = size.height - size.height * limit / top
            drawLine(C.red, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        }
    }
    Row {
        for (p in periods) Text(h.w.month(p.start).take(3), style = T.TypeS, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Hours(hours: List<Int>) {
    val counts = IntArray(24).also { a -> hours.forEach { a[it]++ } }
    val top = maxOf(counts.max(), 1)
    Canvas(Modifier.fillMaxWidth().height(50.dp)) {
        val bw = size.width / 24
        for (i in 0 until 24) {
            val bh = size.height * counts[i] / top
            val late = i >= 22 || i < 5
            drawRect(if (late) C.red else C.inkSoft, Offset(i * bw + bw * .15f, size.height - bh), Size(bw * .7f, bh))
        }
        drawLine(Color.Black.copy(alpha = .3f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
    Row {
        for (lbl in listOf("0", "6", "12", "18", "23")) Text(lbl, style = T.TypeS, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Days(days: List<Int>, h: Home) {
    val counts = IntArray(7).also { a -> days.forEach { a[it - 1]++ } }
    val top = maxOf(counts.max(), 1)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 7) Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val bh = size.height * counts[i] / top
                drawRect(if (counts[i] == top && top > 1) C.ink else C.inkSoft, Offset(size.width * .15f, size.height - bh), Size(size.width * .7f, bh))
            }
            Text(h.w.weekdayShort(i + 1), style = T.TypeS, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/** The monthly tail: set, change, or lift it. */
@Composable
private fun Tail(h: Home, limit: Long, onSet: (Long) -> Unit) {
    val w = h.w
    var v by remember(limit) { mutableStateOf(if (limit > 0) (limit / 100).toString() else "") }
    Label(w.t("Наружка (лимит на период)", "Tail (limit per period)"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(v, { v = it.filter { ch -> ch.isDigit() } }, w.t("без наружки", "no tail"), Modifier.weight(1f), number = true)
        Btn(w.t("Поставить", "Set"), look = Look.PAPER, enabled = (Notice.minor(v) ?: 0) != limit) { onSet(Notice.minor(v) ?: 0) }
    }
    if (limit > 0) Btn(w.t("Снять наружку", "Lift the tail"), Modifier.padding(top = 8.dp), look = Look.GHOST) { v = ""; onSet(0) }
}

@Composable
private fun Edit(h: Home, name: String, alias: String, role: Role, onSave: (String, String, Role) -> Unit, onHide: () -> Unit) {
    val w = h.w
    var open by remember { mutableStateOf(false) }
    Gap(14)
    if (!open) {
        Btn(w.t("Изменить запись", "Edit the record"), Modifier.fillMaxWidth(), look = Look.GHOST) { open = true }
        return
    }
    var n by remember { mutableStateOf(name) }
    var a by remember { mutableStateOf(alias) }
    var r by remember { mutableStateOf(role) }
    Field(n, { n = it }, w.t("что это", "what it is"), max = 30)
    Gap(6)
    Field(a, { a = it }, w.t("кличка", "nickname"), max = 24)
    Gap(8)
    Flow {
        Chip(w.role(Role.SUSPECT), r == Role.SUSPECT) { r = Role.SUSPECT }
        Chip(w.role(Role.CITIZEN), r == Role.CITIZEN) { r = Role.CITIZEN }
    }
    Gap(10)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(w.t("Убрать из картотеки", "Take out of the files"), Modifier.weight(1f), look = Look.GHOST) { onHide() }
        Btn(w.t("Сохранить", "Save"), Modifier.weight(1f), enabled = n.isNotBlank()) { onSave(n.trim(), a.trim(), r); open = false }
    }
}
