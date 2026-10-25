package app.papertrail.ui.office

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.papertrail.domain.Clue
import app.papertrail.domain.Detective
import app.papertrail.domain.GhostState
import app.papertrail.domain.Kind
import app.papertrail.domain.Ledger
import app.papertrail.domain.Report
import app.papertrail.domain.date
import app.papertrail.domain.hour
import app.papertrail.container
import androidx.compose.ui.platform.LocalContext
import app.papertrail.ui.Home
import app.papertrail.ui.Nav
import app.papertrail.ui.Overlay
import app.papertrail.ui.Tab
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Note
import app.papertrail.ui.components.Slab
import app.papertrail.ui.components.TopBar
import app.papertrail.ui.components.paper
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

@Composable
fun OfficeScreen(h: Home, nav: Nav) {
    val w = h.w
    val d = h.now.date(h.zone)
    val pts = Detective.points(h.s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).drawBehind {
        // The desk lamp's pool of light.
        drawCircle(Brush.radialGradient(listOf(C.lamp.copy(alpha = .13f), Color.Transparent), Offset(size.width * .5f, 260.dp.toPx()), 330.dp.toPx()), 330.dp.toPx(), Offset(size.width * .5f, 260.dp.toPx()))
    }) {
        TopBar(w.weekday(d).replaceFirstChar { it.uppercase() } + ", " + w.date(d), w.rank(Detective.rank(pts)) + " · " + w.n(pts, "очко", "очка", "очков", "point", "points")) {
            Icon(Icons.Outlined.Settings, w.t("Настройки", "Settings"), tint = C.smoke, modifier = Modifier.size(44.dp).clickable { nav.push(Overlay.Settings) }.padding(10.dp))
        }
        Gap(8)
        Matchbook(h)
        Gap(18)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(w.t("+ Улика", "+ Clue"), Modifier.weight(1f)) { nav.push(Overlay.Entry()) }
            val env = h.s.envelopes.size
            Btn(if (env > 0) w.t("Конверты · $env", "Envelopes · $env") else w.t("Гонорар", "Fee"), Modifier.weight(1f), look = if (env > 0) Look.PAPER else Look.GHOST) {
                nav.push(if (env > 0) Overlay.Envelopes else Overlay.Entry(fee = true))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Dispatches(h, nav)
            TodayClues(h, nav)
            EveningReport(h)
            Gap(24)
        }
    }
}

/** The matchbook: today's advance in big figures, and a match for every day of the period, the past ones burnt. */
@Composable
private fun Matchbook(h: Home) {
    val w = h.w
    val t = h.today
    val over = t.leftToday < 0
    Box(Modifier.fillMaxWidth().padding(horizontal = 34.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.rotate(-1.5f).fillMaxWidth()
                .drawBehind { drawRect(Color.Black.copy(alpha = .5f), Offset(5.dp.toPx(), 7.dp.toPx()), size) }
                .paper(C.paperHi),
        ) {
            Box(Modifier.fillMaxWidth().background(C.ink).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(w.t("АВАНС НА СЕГОДНЯ", "TODAY'S ADVANCE"), style = T.Label.copy(color = C.paper))
            }
            Text(
                w.money(t.leftToday), style = T.Big.copy(color = if (over) C.red else C.ink, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp),
            )
            Text(
                if (over) w.t("перерасход за день · норма ${w.money(t.perDay)}", "over for the day · the norm is ${w.money(t.perDay)}")
                else if (t.spentToday > 0) w.t("из ${w.money(t.perDay)} · ушло ${w.money(t.spentToday)}", "of ${w.money(t.perDay)} · ${w.money(t.spentToday)} gone")
                else w.t("ни одной улики сегодня", "not a clue today"),
                style = T.TypeS.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(),
            )
            Matches(t.period.days, t.period.days - t.daysLeft, Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().background(C.paperDim).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(w.t("В КАССЕ", "IN THE TILL"), style = T.Label.copy(color = C.inkSoft))
                    Text(w.money(t.left), style = T.Type.copy(color = if (t.left < 0) C.red else C.ink))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(w.t("ДО ГОНОРАРА", "TO THE FEE"), style = T.Label.copy(color = C.inkSoft))
                    Text(w.n(t.daysLeft, "день", "дня", "дней", "day", "days"), style = T.Type)
                }
            }
            if (h.settings.advance <= 0) Text(
                w.t("Аванс не назначен — задайте его в настройках.", "No advance set; name it in settings."),
                style = T.TypeS.copy(color = C.red), modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun Matches(total: Int, burnt: Int, modifier: Modifier) {
    Canvas(modifier) {
        val n = total.coerceIn(1, 31)
        val step = size.width / n
        val stick = size.height * .8f
        for (i in 0 until n) {
            val x = step * (i + .5f)
            val gone = i < burnt
            val top = if (gone) size.height * .35f else 0f
            drawLine(if (gone) Color(0xFF6B5A46) else Color(0xFFCDB58C), Offset(x, size.height), Offset(x, top + stick * .12f), step * .32f)
            drawCircle(if (gone) C.ink else C.red, step * .3f, Offset(x, top + stick * .1f))
            if (gone) drawLine(Color(0xFF2B2622), Offset(x, top + stick * .12f), Offset(x, top + stick * .35f), step * .32f, StrokeCap.Round)
        }
    }
}

/** What the informants and the files have to say: open cases, bills coming, ghosts, cold cases, tails. */
@Composable
private fun Dispatches(h: Home, nav: Nav) {
    val w = h.w
    val s = h.s
    val items = mutableListOf<Pair<String, () -> Unit>>()
    for (c in s.openCases) items += (w.t("Новое дело №${c.number}: ", "New case no. ${c.number}: ") + w.caseLead(c, s)) to { nav.push(Overlay.CaseOpen(c.id)) }
    for ((b, due) in Ledger.dueSoon(s.bills, h.today.period, h.now.date(h.zone))) {
        val days = (due.toEpochDay() - h.now.date(h.zone).toEpochDay()).toInt()
        val whenText = when { days < 0 -> w.t("просрочен", "overdue"); days == 0 -> w.t("сегодня", "today"); days == 1 -> w.t("завтра", "tomorrow"); else -> w.t("через ${w.n(days, "день", "дня", "дней", "", "")}", "in $days days") }
        items += w.t("Осведомитель шепчет: «${b.name}» — ${w.money(b.amount)}, $whenText.", "The informant whispers: «${b.name}», ${w.money(b.amount)}, $whenText.") to { nav.tab(Tab.FILES) }
    }
    for (g in s.ghosts.filter { it.state == GhostState.SEEN || it.returned }) {
        items += (if (g.returned) w.t("Призрак вернулся: «${g.label}», ${w.money(g.amount)}.", "A ghost came back: «${g.label}», ${w.money(g.amount)}.")
            else w.t("Кто-то приходит каждое ${g.day}-е: «${g.label}», ${w.money(g.amount)}. Призрак?", "Something comes every month on the ${g.day}th: «${g.label}», ${w.money(g.amount)}. A ghost?")) to { nav.tab(Tab.FILES) }
    }
    val cold = s.cold
    if (cold.isNotEmpty()) items += w.t("Висяков: ${cold.size}. Наличные на ${w.money(cold.sumOf { it.amount })} — куда ушли?", "Cold cases: ${cold.size}. ${w.money(cold.sumOf { it.amount })} in cash. Where did it go?") to { nav.tab(Tab.BOARD) }
    val spent = Ledger.bySuspect(s.clues, h.today.period, h.zone)
    for (sus in s.files.filter { it.limit > 0 }) {
        val v = spent[sus.id] ?: 0
        if (v * 10 >= sus.limit * 8 && v <= sus.limit) items += w.t("${w.alias(sus)} у черты: ${w.money(v)} из ${w.money(sus.limit)}.", "${w.alias(sus)} near the line: ${w.money(v)} of ${w.money(sus.limit)}.") to { nav.push(Overlay.Dossier(sus.id)) }
    }
    for (sus in s.files.filter { it.banUntil > h.now }) {
        items += w.t("${w.alias(sus)} под запретом до ${w.date(sus.banUntil.date(h.zone))}.", "${w.alias(sus)} is banned until ${w.date(sus.banUntil.date(h.zone))}.") to { nav.push(Overlay.Dossier(sus.id)) }
    }
    if (items.isEmpty()) return
    Label(w.t("Донесения", "Dispatches"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((text, go) in items) Slab(onClick = go) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).background(C.red))
                Text(text, style = T.Body, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun TodayClues(h: Home, nav: Nav) {
    val w = h.w
    val d = h.now.date(h.zone)
    val list = h.s.clues.filter { it.at.date(h.zone) == d }.sortedByDescending { it.at }
    if (list.isEmpty()) return
    Label(w.t("Улики за сегодня", "Today's clues"))
    Note {
        for ((i, c) in list.withIndex()) {
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.paperDim))
            ClueRow(h, c) { nav.push(Overlay.Entry(c.id)) }
        }
    }
}

/** One clue, typed: the time, who, where, and how much. */
@Composable
fun ClueRow(h: Home, c: Clue, dark: Boolean = false, onClick: (() -> Unit)? = null) {
    val w = h.w
    val ink = if (dark) C.paper else C.ink
    val soft = if (dark) C.smoke else C.inkSoft
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(w.time(c.at, h.zone), style = T.TypeS.copy(color = soft), modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            val who = when {
                c.kind == Kind.FEE -> w.t("Гонорар", "Fee")
                c.kind == Kind.VAULT -> w.t("Сейф", "Safe")
                c.cash && !c.solved -> w.t("Наличные · висяк", "Cash · cold case")
                else -> w.alias(h.s.suspect(c.suspectId))
            }
            Text(who, style = T.Type.copy(color = ink), maxLines = 1)
            val sub = listOf(c.place, c.note).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = T.TypeS.copy(color = soft), maxLines = 1)
        }
        val late = Detective.late(c.at.hour(h.zone)) && c.kind == Kind.SPEND
        Text(
            w.money(if (c.kind == Kind.FEE) c.amount else -c.amount).let { if (c.kind == Kind.FEE) "+$it" else it },
            style = T.Type.copy(color = if (c.kind == Kind.FEE) soft else if (late) C.red else ink),
        )
    }
}

/** The day's typed report: today's after six in the evening, yesterday's before. */
@Composable
private fun EveningReport(h: Home) {
    val w = h.w
    val lines = LocalContext.current.container.lines
    val today = h.now.date(h.zone)
    val evening = java.time.Instant.ofEpochMilli(h.now).atZone(h.zone).hour >= 18
    val day = if (evening) today else today.minusDays(1)
    if (!evening && h.s.clues.none { it.at.date(h.zone) <= day }) return
    val f = Report.facts(h.s, h.settings.plan, day, h.zone)
    val line = Report.pick(lines, f) ?: return
    Label(if (evening) w.t("Рапорт за сегодня", "Today's report") else w.t("Рапорт за вчера", "Yesterday's report"))
    Note(color = C.paperHi) {
        Text(w.line(line, f, h.s), style = T.Type)
        Text("— " + w.t("из дневника сыщика", "from the detective's diary"), style = T.TypeS, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.End)
    }
}

