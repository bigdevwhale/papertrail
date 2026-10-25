package app.papertrail.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Case
import app.papertrail.domain.Verdict
import app.papertrail.domain.date
import app.papertrail.sound.Snd
import app.papertrail.ui.Home
import app.papertrail.ui.Nav
import app.papertrail.ui.Overlay
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Grain
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Note
import app.papertrail.ui.components.Portrait
import app.papertrail.ui.components.Slab
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.SuspectPicker
import app.papertrail.ui.components.TopBar
import app.papertrail.ui.components.pin
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

/** Cork with its holes and grain. */
fun Modifier.cork() = this.background(C.cork).drawWithContent {
    drawRect(Brush.radialGradient(listOf(C.corkHi, C.cork, Color(0xFF4E4032)), center, size.maxDimension * .7f))
    drawRect(Grain.brush); drawRect(Grain.brush)
    drawContent()
}

@Composable
fun BoardScreen(h: Home, nav: Nav) {
    val w = h.w
    val s = h.s
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(w.t("Доска", "The board"), if (s.openCases.isEmpty()) w.t("дел в работе нет", "no open cases") else w.t("в работе: ${w.n(s.openCases.size, "дело", "дела", "дел", "", "")}", "${w.n(s.openCases.size, "", "", "", "case", "cases")} open"))
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (s.openCases.isEmpty()) Note(Modifier.padding(top = 8.dp).rotate(-1f)) {
                Text(
                    w.t("Пока тихо. Дело откроется само, когда в уликах проступит почерк: ночные вылазки, тысяча мелочей, внезапный размах, один и тот же день недели или нарушенная наружка.",
                        "Quiet for now. A case opens by itself when the clues show a pattern: night work, death by small change, a sudden spree, the same weekday, or a broken tail."),
                    style = T.Type,
                )
            }
            else Column(Modifier.fillMaxWidth().cork().padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((i, c) in s.openCases.withIndex()) CaseCard(h, c, if (i % 2 == 0) -1.4f else 1.2f) { nav.push(Overlay.CaseOpen(c.id)) }
            }
            Cold(h)
            if (s.closedCases.isNotEmpty()) {
                Label(w.t("Архив", "Archive"))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in s.closedCases.take(30)) Slab(onClick = { nav.push(Overlay.CaseOpen(c.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.caseTitle(c) + " · " + w.alias(s.suspect(c.suspectId)), style = T.Body)
                                Text(w.pattern(c.pattern) + (c.motive?.let { " · " + w.motive(it) } ?: "") + " · " + w.date((c.closedAt ?: c.openedAt).date(h.zone)), style = T.Small)
                            }
                            Stamp(w.verdict(c.verdict), color = if (c.verdict == Verdict.GUILTY) C.red else C.fog, angle = -6f)
                        }
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CaseCard(h: Home, c: Case, angle: Float, onClick: () -> Unit) {
    val w = h.w
    Box {
        Note(Modifier.rotate(angle), color = C.paperHi, onClick = onClick) {
            Row {
                Portrait(h.s.suspect(c.suspectId), Modifier.width(64.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(w.caseTitle(c).uppercase(), style = T.Label.copy(color = C.red))
                    Text(w.pattern(c.pattern), style = T.Heading.copy(color = C.ink))
                    Text(w.caseLead(c, h.s), style = T.TypeS.copy(color = C.ink))
                }
            }
        }
        Canvas(Modifier.align(Alignment.TopCenter).width(20.dp).height(20.dp)) { pin(Offset(size.width / 2, size.height / 2), size.width * .32f) }
    }
}

/** Cash nobody accounted for. Solving one means naming who got it. */
@Composable
private fun Cold(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    val cold = h.s.cold
    if (cold.isEmpty()) return
    Label(w.t("Висяки", "Cold cases"))
    var open by remember { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in cold) Slab(onClick = { open = if (open == e.id) null else e.id }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.t("Наличные, ", "Cash, ") + w.money(e.amount), style = T.Body)
                    Text(w.date(e.at.date(h.zone)) + (if (e.place.isNotBlank()) " · " + e.place else ""), style = T.Small)
                }
                Text(if (open == e.id) "×" else w.t("РАСКРЫТЬ", "SOLVE"), style = T.Button.copy(color = C.red))
            }
            if (open == e.id) {
                Text(w.t("На что ушли эти деньги?", "What did this money go on?"), style = T.Small, modifier = Modifier.padding(vertical = 8.dp))
                SuspectPicker(w, h.s.files, null, size = 58.dp) { sus ->
                    c.sfx.play(Snd.STAMP); c.sfx.feel(30, 120)
                    c.write { repo.solve(e.id, sus.id) }
                    open = null
                }
                Btn(w.t("Не помню — закрыть так", "Can't recall, close it"), Modifier.fillMaxWidth().padding(top = 10.dp), look = Look.GHOST) {
                    c.write { repo.solve(e.id, null) }
                    open = null
                }
            }
        }
    }
}
