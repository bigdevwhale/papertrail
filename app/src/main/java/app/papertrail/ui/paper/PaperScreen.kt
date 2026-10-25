package app.papertrail.ui.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papertrail.domain.Gazette
import app.papertrail.domain.Issue
import app.papertrail.domain.Lead
import app.papertrail.domain.Period
import app.papertrail.ui.Home
import app.papertrail.ui.components.Chip
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Portrait
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.TopBar
import app.papertrail.ui.components.paper
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.Serif
import app.papertrail.ui.theme.T

@Composable
fun PaperScreen(h: Home) {
    val w = h.w
    val plan = h.settings.plan
    val first = Gazette.firstPeriod(h.s, plan, h.now, h.zone)
    val periods = generateSequence(h.today.period) { it.prev(plan.payday) }.takeWhile { it.id >= first.id }.take(12).toList()
    var picked by remember { mutableStateOf<Period?>(null) }
    val p = picked ?: periods.first()
    val issue = Gazette.issue(h.s, plan, p, h.now, h.zone, first)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(w.t("Газета", "The paper"), w.t("выходит раз в период", "out once a period"))
        if (periods.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (q in periods) Chip(w.month(q.start).replaceFirstChar { it.uppercase() } + " " + q.start.year % 100, q.id == p.id) { picked = q }
        }
        Gap(12)
        Front(h, issue, Modifier.padding(horizontal = 12.dp))
        Gap(24)
    }
}

private val Masthead = T.Title.copy(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, color = C.ink, textAlign = TextAlign.Center, letterSpacing = 1.sp)
private val Head = T.Title.copy(fontSize = 27.sp, lineHeight = 29.sp, color = C.ink, textAlign = TextAlign.Center)
private val Deck = T.Italic.copy(color = C.ink, textAlign = TextAlign.Center)
private val Col = T.Body.copy(color = C.ink, fontSize = 13.sp, lineHeight = 17.sp)
private val ColHead = T.Label.copy(color = C.ink, fontWeight = FontWeight.Bold)

@Composable
private fun Rule(thick: Boolean = false) = Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(if (thick) 2.dp else 1.dp).background(C.ink))

@Composable
fun Front(h: Home, i: Issue, modifier: Modifier = Modifier) {
    val w = h.w
    val s = h.s
    val (title, deck) = w.headline(i, s)
    Box(modifier) {
        Column(Modifier.fillMaxWidth().drawBehind { drawRect(Color.Black.copy(alpha = .5f), Offset(5.dp.toPx(), 6.dp.toPx()), size) }.paper(C.paperHi).padding(14.dp)) {
            Text(w.paperName().uppercase(), style = Masthead, modifier = Modifier.fillMaxWidth())
            Rule(true)
            Row {
                Text(w.t("Выпуск №${i.number}", "Issue no. ${i.number}"), style = T.TypeS, modifier = Modifier.weight(1f))
                Text(w.date(i.period.start) + " — " + w.date(i.period.end.minusDays(1)), style = T.TypeS)
            }
            Rule()
            Gap(8)
            Text(title, style = Head, modifier = Modifier.fillMaxWidth())
            Gap(6)
            Text(deck, style = Deck, modifier = Modifier.fillMaxWidth())
            Gap(10)
            Rule()
            Row(Modifier.fillMaxWidth()) {
                // Left column: the wanted poster.
                val star = s.suspect(i.star) ?: s.suspect(i.top.firstOrNull()?.first)
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(if (i.lead == Lead.CAUGHT) w.t("ЗАДЕРЖАН", "IN CUSTODY") else w.t("РАЗЫСКИВАЕТСЯ", "WANTED"), style = ColHead)
                    Gap(4)
                    Portrait(star, Modifier.fillMaxWidth())
                    Text(w.alias(star), style = Col.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
                    star?.let { Text(w.t("За период: ", "This period: ") + w.money(i.now[it.id] ?: 0), style = Col) }
                }
                Box(Modifier.width(1.dp).height(250.dp).background(C.ink))
                // Right column: the figures.
                Column(Modifier.weight(1.2f).padding(start = 10.dp)) {
                    Text(w.t("СВОДКА", "THE FIGURES"), style = ColHead)
                    Gap(4)
                    Fig(w.t("Аванс", "Advance"), w.money(i.advance))
                    Fig(w.t("Потрачено", "Spent"), w.money(i.spent))
                    Fig(if (i.left >= 0) w.t("Осталось", "Left") else w.t("Перерасход", "Over"), w.money(kotlin.math.abs(i.left)))
                    if (i.fees > 0) Fig(w.t("Гонорары", "Fees"), w.money(i.fees))
                    if (i.intoSafe != 0L) Fig(w.t("В сейф", "To the safe"), w.money(i.intoSafe))
                    Fig(w.t("Дней без улик", "Clean days"), "${i.quietDays} / ${i.days}")
                    Gap(8)
                    Text(w.t("САМЫЕ РАЗЫСКИВАЕМЫЕ", "MOST WANTED"), style = ColHead)
                    for ((n, top) in i.top.withIndex()) Text("${n + 1}. ${w.alias(s.suspect(top.first))} — ${w.money(top.second)}", style = Col)
                    if (i.top.isEmpty()) Text(w.t("никого", "nobody"), style = Col)
                }
            }
            Rule()
            Text(w.t("КРИМИНАЛЬНАЯ ХРОНИКА", "CRIME BEAT"), style = ColHead)
            if (i.closed.isEmpty() && i.opened.isEmpty()) Text(w.t("За период громких дел не было.", "No big cases this period."), style = Col)
            for (c in i.closed) Text("• " + w.caseTitle(c) + ", " + w.alias(s.suspect(c.suspectId)) + ": " + w.verdict(c.verdict) + (c.motive?.let { " (" + w.motive(it) + ")" } ?: ""), style = Col)
            for (c in i.opened.filter { it.open }) Text("• " + w.caseTitle(c) + ", " + w.alias(s.suspect(c.suspectId)) + ": " + w.t("следствие идёт", "under investigation"), style = Col)
            if (i.ghosts.isNotEmpty()) {
                Gap(6)
                Text(w.t("ПРИЗРАКИ В ГОРОДЕ", "GHOSTS IN TOWN"), style = ColHead)
                for (g in i.ghosts) Text("• " + g.label + " — " + w.money(g.amount) + ", " + w.ghost(g.state), style = Col)
            }
            Rule()
            Text(
                w.t("Сейф «Несгораемый». В нём ${w.money(s.vault)}. Надёжно с 1946 года.", "The Fireproof Safe Co. Holding ${w.money(s.vault)}. Trusted since 1946."),
                style = Col.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!i.final) Stamp(w.t("Вечерний выпуск", "Late edition"), Modifier.padding(top = 46.dp, end = 4.dp).align(androidx.compose.ui.Alignment.TopEnd), angle = 10f)
    }
}

@Composable
private fun Fig(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, style = Col, modifier = Modifier.weight(1f))
        Text(value, style = Col.copy(fontWeight = FontWeight.Bold))
    }
}
