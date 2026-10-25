package app.papertrail.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Case
import app.papertrail.domain.Clue
import app.papertrail.domain.Detective
import app.papertrail.domain.Ledger
import app.papertrail.domain.Measure
import app.papertrail.domain.Motive
import app.papertrail.domain.Notice
import app.papertrail.domain.Pattern
import app.papertrail.domain.Verdict
import app.papertrail.domain.date
import app.papertrail.domain.hour
import app.papertrail.sound.Snd
import app.papertrail.ui.Home
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
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.paper
import app.papertrail.ui.components.pin
import app.papertrail.ui.components.thread
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the clue notes hang around the photo: x as a share of the board's width, y in dp. */
private val SPOTS = listOf(0.02f to 34, 0.69f to 30, 0.03f to 168, 0.35f to 184, 0.67f to 162, 0.04f to 300, 0.36f to 314, 0.66f to 296)
private val ANGLES = listOf(-4f, 3f, 2f, -2f, 4f, -3f, 1.5f, -1.5f)

@Composable
fun CaseSheet(h: Home, id: Long, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    val case = h.s.case(id) ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val clues = case.clues.mapNotNull { h.s.clue(it) }
    LaunchedEffect(id) { c.sfx.play(Snd.PIN, .8f); delay(120); c.sfx.play(Snd.PIN, .6f) }

    Sheet(w.caseTitle(case) + " · " + w.pattern(case.pattern), onClose, w.t("Закрыть", "Close")) {
        Board(h, case, clues)
        Gap(14)
        Note(color = C.paperHi) {
            Text(w.caseLead(case, h.s), style = T.Type)
        }
        Text(w.hunch(case), style = T.Pencil.copy(color = C.fog), modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        if (case.open) Trial(h, case, onClose)
        else Closed(h, case)
        Gap(16)
    }
}

@Composable
private fun Board(h: Home, case: Case, clues: List<Clue>) {
    val w = h.w
    val shown = clues.takeLast(SPOTS.size)
    val more = clues.size - shown.size
    val pairs = Detective.threads(case, shown, h.zone)
    // The board grows with the clues: a row of two, then rows of three.
    val tall = when { shown.size <= 2 -> 160; shown.size <= 5 -> 290; else -> 420 }
    BoxWithConstraints(Modifier.fillMaxWidth().height(tall.dp).cork()) {
        val bw = maxWidth
        val noteW = bw * .3f
        val photoW = 86.dp
        val photoX = (bw - photoW) / 2
        fun spot(i: Int): Pair<Dp, Dp> = (bw * SPOTS[i].first) to SPOTS[i].second.dp

        Box(Modifier.offset(photoX, 14.dp).width(photoW).rotate(1f)) {
            Portrait(h.s.suspect(case.suspectId), Modifier.fillMaxWidth())
        }
        Text(
            w.alias(h.s.suspect(case.suspectId)).uppercase(), style = T.Label.copy(color = C.paper),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 124.dp),
        )
        for ((i, clue) in shown.withIndex()) {
            val (x, y) = spot(i)
            Box(Modifier.offset(x, y).width(noteW).rotate(ANGLES[i]).paper(C.paperHi).padding(horizontal = 7.dp, vertical = 6.dp)) {
                Column {
                    val late = Detective.late(clue.at.hour(h.zone))
                    Text(w.shortDate(clue.at.date(h.zone)) + " " + w.weekdayShort(clue.at.date(h.zone).dayOfWeek.value), style = T.TypeS.copy(color = if (case.pattern == Pattern.WEEKDAY) C.red else C.inkSoft))
                    Text(w.time(clue.at, h.zone), style = T.TypeS.copy(color = if (late && case.pattern == Pattern.NIGHT) C.red else C.inkSoft))
                    Text(w.money(clue.amount), style = T.Type.copy(color = C.ink))
                    if (clue.place.isNotBlank()) Text(clue.place, style = T.TypeS, maxLines = 1)
                    if (i == shown.lastIndex && more > 0) Text(w.t("+ ещё $more", "+ $more more"), style = T.TypeS.copy(color = C.red))
                }
            }
        }
        // Threads from the photo to every note, and between notes that share a trait; pins on top.
        Canvas(Modifier.fillMaxSize()) {
            val top = Offset(size.width / 2, 14.dp.toPx() + 6.dp.toPx())
            val pins = shown.indices.map { i -> val (x, y) = spot(i); Offset(x.toPx() + noteW.toPx() / 2, y.toPx() + 3.dp.toPx()) }
            val wd = 1.6.dp.toPx()
            for (p in pins) thread(top, p, wd, .05f)
            for ((a, b) in pairs) thread(pins[a], pins[b], wd, .12f)
            for (p in pins) pin(p, 5.dp.toPx())
            pin(top, 6.dp.toPx())
        }
    }
}

/** Naming the motive and handing down the verdict, with the stamp at the end. */
@Composable
private fun Trial(h: Home, case: Case, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    var motive by remember { mutableStateOf<Motive?>(null) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var measure by remember { mutableStateOf(Measure.WATCH) }
    val sus = h.s.suspect(case.suspectId)
    val usual = Ledger.bySuspect(h.s.clues, h.today.period.prev(h.settings.payday), h.zone)[case.suspectId] ?: 0
    var limit by remember { mutableStateOf(w.amount(((maxOf(usual, case.sum) * 8 / 10) / 10000) * 10000).replace(" ", "")) }
    var stamped by remember { mutableStateOf<Verdict?>(null) }
    val scope = rememberCoroutineScope()
    val drop = remember { Animatable(2.2f) }

    Label(w.t("Мотив", "Motive"))
    Flow { for (m in Motive.entries) Chip(w.motive(m), motive == m) { motive = m } }
    Label(w.t("Вердикт", "Verdict"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(w.t("Оправдать", "Acquit"), Modifier.weight(1f), look = if (verdict == Verdict.ACQUITTED) Look.PAPER else Look.GHOST) { verdict = Verdict.ACQUITTED }
        Btn(w.t("Виновен", "Guilty"), Modifier.weight(1f), look = if (verdict == Verdict.GUILTY) Look.RED else Look.GHOST) { verdict = Verdict.GUILTY }
    }
    if (verdict == Verdict.ACQUITTED) Text(
        w.t("Оправдательный приговор — тоже раскрытое дело. Трата была осознанной, и это главное.", "An acquittal is a solved case too. The spending was a choice, and that's what matters."),
        style = T.Small, modifier = Modifier.padding(top = 8.dp),
    )
    if (verdict == Verdict.GUILTY) {
        Label(w.t("Мера", "Measure"))
        Flow { for (m in listOf(Measure.WATCH, Measure.LIMIT, Measure.BAN)) Chip(w.measure(m), measure == m) { measure = m } }
        Text(w.measureSub(measure), style = T.Small, modifier = Modifier.padding(top = 6.dp))
        if (measure == Measure.LIMIT) {
            Gap(6)
            Field(limit, { limit = it.filter { ch -> ch.isDigit() } }, "0", number = true)
            if (sus != null && sus.limit > 0) Text(w.t("Сейчас: ${w.money(sus.limit)} в месяц", "Now: ${w.money(sus.limit)} a month"), style = T.Small)
        }
    }
    Gap(16)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (stamped == null) Btn(w.t("Закрыть дело", "Close the case"), Modifier.fillMaxWidth(), enabled = verdict != null && motive != null) {
            val v = verdict ?: return@Btn
            stamped = v
            val lim = (Notice.minor(limit) ?: 0)
            scope.launch {
                drop.animateTo(1f, tween(260))
                c.sfx.play(Snd.STAMP); c.sfx.feel(40, 200)
                delay(1100)
                // Written after the stamp has had its moment: the closed case would replace this view at once.
                val m = motive; val ms = measure
                onClose()
                c.write { repo.verdict(case.id, v, m, ms, lim) }
            }
        } else Box(Modifier.height(80.dp).scale(drop.value).alpha((2.4f - drop.value).coerceIn(0f, 1f)), contentAlignment = Alignment.Center) {
            Stamp(if (stamped == Verdict.GUILTY) w.t("Виновен", "Guilty") else w.t("Оправдан", "Acquitted"), color = if (stamped == Verdict.GUILTY) C.red else C.paper, angle = -9f)
        }
    }
}

@Composable
private fun Closed(h: Home, case: Case) {
    val w = h.w
    Gap(16)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stamp(w.verdict(case.verdict), color = if (case.verdict == Verdict.GUILTY) C.red else C.paper)
        Column(Modifier.padding(start = 18.dp)) {
            case.motive?.let { Text(w.t("Мотив: ", "Motive: ") + w.motive(it), style = T.Body) }
            if (case.measure != Measure.NONE) Text(w.t("Мера: ", "Measure: ") + w.measure(case.measure), style = T.Small)
            case.closedAt?.let { Text(w.t("Закрыто ", "Closed ") + w.date(it.date(h.zone)), style = T.Small) }
        }
    }
}
