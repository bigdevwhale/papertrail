package app.papertrail.ui.safe

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Kind
import app.papertrail.domain.Notice
import app.papertrail.domain.Ticket
import app.papertrail.domain.date
import app.papertrail.sound.Snd
import app.papertrail.ui.Home
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Field
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Grain
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.LocalToast
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Slab
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.TopBar
import app.papertrail.ui.components.paper
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

@Composable
fun SafeScreen(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    var dial by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(w.t("Сейф", "The safe"), w.t("вещдоки и билеты из этого города", "evidence money and tickets out of this town"))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Door(h, dial)
            Move(h) { amount ->
                dial += if (amount > 0) 120f else -120f
                repeat(3) { c.sfx.play(Snd.DIAL, .8f, 1f + it * .1f) }
                c.write { repo.safe(amount) }
            }
            Tickets(h)
            History(h)
            Gap(24)
        }
    }
}

/** The safe's door: steel, a dial, and a brass plate with what's inside. */
@Composable
private fun Door(h: Home, dial: Float) {
    val w = h.w
    Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(10.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = .5f), Offset(6.dp.toPx(), 8.dp.toPx()), size, r)
            drawRoundRect(Brush.linearGradient(listOf(C.steelHi, C.steel, Color(0xFF2E2C2A)), Offset.Zero, Offset(size.width, size.height)), size = size, cornerRadius = r)
            val inset = 12.dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = .35f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
            for (p in listOf(Offset(inset * 2, inset * 2), Offset(size.width - inset * 2, inset * 2), Offset(inset * 2, size.height - inset * 2), Offset(size.width - inset * 2, size.height - inset * 2)))
                drawCircle(Color(0xFF8A8580), 4.dp.toPx(), p)
            // The dial, on the left.
            val dc = Offset(size.width * .25f, size.height * .5f)
            val dr = size.height * .28f
            drawCircle(Color(0xFF1E1C1A), dr * 1.08f, dc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF9A958E), Color(0xFF4F4B47)), dc, dr), dr, dc)
            rotate(dial, dc) {
                for (i in 0 until 40) {
                    val long = i % 5 == 0
                    rotate(i * 9f, dc) { drawLine(Color(0xFF1E1C1A), Offset(dc.x, dc.y - dr), Offset(dc.x, dc.y - dr * if (long) .78f else .88f), 1.5.dp.toPx()) }
                }
                drawCircle(Color(0xFF34312E), dr * .35f, dc)
            }
            drawLine(C.red, Offset(dc.x, dc.y - dr * 1.2f), Offset(dc.x, dc.y - dr * 1.02f), 2.dp.toPx())
            // The handle.
            val hc = Offset(size.width * .25f, size.height * .5f)
            drawCircle(Color(0xFF6B6763), 6.dp.toPx(), hc)
            drawRect(Grain.brush)
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 26.dp).rotate(-1f)
                .drawBehind { drawRect(Color.Black.copy(alpha = .4f), Offset(3.dp.toPx(), 3.dp.toPx()), size) }
                .paper(C.brass).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(w.t("В СЕЙФЕ", "IN THE SAFE"), style = T.Label.copy(color = C.ink))
            Text(w.money(h.s.vault), style = T.Heading.copy(color = C.ink))
            if (h.s.inTickets > 0) Text(w.t("свободно ${w.money(h.s.vaultFree)}", "${w.money(h.s.vaultFree)} free"), style = T.TypeS.copy(color = C.ink))
        }
    }
}

/** Money in or out. What goes in comes off the current advance; what comes out goes back to it. */
@Composable
private fun Move(h: Home, onMove: (Long) -> Unit) {
    val w = h.w
    var v by remember { mutableStateOf("") }
    val a = Notice.minor(v) ?: 0
    Gap(14)
    Field(v, { v = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } }, w.t("сумма", "amount"), number = true)
    Gap(8)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(w.t("Изъять в сейф", "Into the safe"), Modifier.weight(1f), enabled = a > 0) { onMove(a); v = "" }
        Btn(w.t("Выдать", "Take out"), Modifier.weight(1f), look = Look.GHOST, enabled = a > 0 && a <= h.s.vaultFree) { onMove(-a); v = "" }
    }
    Text(
        w.t("Изъятое из аванса уходит в сейф и в траты не считается. Выданное возвращается в кассу.", "Money put away comes off the advance and isn't counted as spending. Money taken out goes back to the till."),
        style = T.Small, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Tickets(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    val toast = LocalToast.current
    var adding by remember { mutableStateOf(false) }
    Label(w.t("Билеты из этого города", "Tickets out of this town"))
    val open = h.s.tickets.filter { it.doneAt == null }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((i, t) in open.withIndex()) TicketCard(h, t, if (i % 2 == 0) -1f else 1f,
            onStamp = { a ->
                c.sfx.play(Snd.STAMP); c.sfx.feel(30, 150)
                c.write { if (!repo.stamp(t.id, a)) toast.show(w.t("В сейфе нет свободных денег для штампа.", "No free money in the safe for a stamp.")) }
            },
            onRedeem = { c.sfx.play(Snd.BELL); c.write { repo.redeem(t.id) } },
            onDelete = { c.write { repo.deleteTicket(t) } },
        )
    }
    Gap(10)
    if (!adding) Btn(w.t("+ Новый билет", "+ New ticket"), Modifier.fillMaxWidth(), look = Look.GHOST) { adding = true }
    else NewTicket(h) { title, target ->
        if (title != null) c.write { repo.saveTicket(Ticket(title = title, target = target, createdAt = 0, style = open.size % 3)) }
        adding = false
    }
    val done = h.s.tickets.filter { it.doneAt != null }
    if (done.isNotEmpty()) {
        Label(w.t("Использованные билеты", "Used tickets"))
        for (t in done) Text("✓ " + t.title + " · " + w.money(t.saved) + " · " + w.date(t.doneAt!!.date(h.zone)), style = T.Small)
    }
}

/** A ticket with ten stamp boxes; each filled box is a tenth of the way. */
@Composable
private fun TicketCard(h: Home, t: Ticket, angle: Float, onStamp: (Long) -> Unit, onRedeem: () -> Unit, onDelete: () -> Unit) {
    val w = h.w
    var v by remember { mutableStateOf("") }
    val full = t.saved >= t.target
    Column(
        Modifier.fillMaxWidth().rotate(angle)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = .45f), Offset(4.dp.toPx(), 5.dp.toPx()), size)
            }
            .paper(listOf(C.paperHi, Color(0xFFE6DCC3), Color(0xFFDCE0D6))[t.style % 3])
            .drawBehind {
                // The perforated stub.
                val x = size.width * .78f
                var y = 6.dp.toPx()
                while (y < size.height) { drawCircle(C.night, 2.dp.toPx(), Offset(x, y)); y += 9.dp.toPx() }
            }
            .padding(14.dp),
    ) {
        Row {
            Column(Modifier.weight(.78f)) {
                Text(w.t("БИЛЕТ В ОДИН КОНЕЦ", "ONE-WAY TICKET"), style = T.Label.copy(color = C.inkSoft))
                Text(t.title, style = T.Heading.copy(color = C.ink))
                Text(w.money(t.saved) + " / " + w.money(t.target), style = T.Type)
            }
            Box(Modifier.weight(.22f).padding(start = 8.dp), contentAlignment = Alignment.Center) {
                Text("№${t.id.toString().padStart(4, '0')}", style = T.TypeS, textAlign = TextAlign.Center)
            }
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val n = ((t.saved * 10) / maxOf(t.target, 1)).toInt().coerceIn(0, 10)
            for (i in 0 until 10) Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(C.inkSoft, style = Stroke(1.dp.toPx()))
                    if (i < n) rotate(-12f + i * 5) { drawCircle(C.red.copy(alpha = .8f), size.minDimension * .38f, style = Stroke(2.dp.toPx())); drawCircle(C.red.copy(alpha = .5f), size.minDimension * .14f) }
                }
            }
        }
        Gap(10)
        if (full) Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp(w.t("Оплачен", "Paid in full"), angle = -6f)
            Box(Modifier.weight(1f))
            Btn(w.t("В путь", "Use it"), onClick = onRedeem)
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(v, { v = it.filter { ch -> ch.isDigit() } }, w.t("сколько", "how much"), Modifier.weight(1f), number = true)
            Btn(w.t("Штамп", "Stamp"), enabled = (Notice.minor(v) ?: 0) > 0) { onStamp(Notice.minor(v) ?: 0); v = "" }
            Text("×", style = T.Heading.copy(color = C.inkSoft), modifier = Modifier.padding(start = 4.dp).clickable(onClick = onDelete).padding(6.dp))
        }
    }
}

@Composable
private fun NewTicket(h: Home, onDone: (String?, Long) -> Unit) {
    val w = h.w
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    Slab {
        Field(title, { title = it }, w.t("куда: «Море в июле»", "where to: The sea in July"), max = 32)
        Gap(6)
        Field(target, { target = it.filter { ch -> ch.isDigit() } }, w.t("сколько нужно", "how much it takes"), number = true)
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(w.t("Отмена", "Cancel"), Modifier.weight(1f), look = Look.GHOST) { onDone(null, 0) }
            val a = Notice.minor(target) ?: 0
            Btn(w.t("Выписать", "Issue"), Modifier.weight(1f), enabled = title.isNotBlank() && a > 0) { onDone(title.trim(), a) }
        }
    }
}

@Composable
private fun History(h: Home) {
    val w = h.w
    val moves = h.s.clues.filter { it.kind == Kind.VAULT }.sortedByDescending { it.at }.take(12)
    if (moves.isEmpty()) return
    Label(w.t("Журнал сейфа", "Safe log"))
    for (m in moves) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(w.date(m.at.date(h.zone)) + (if (m.note.isNotBlank()) " · " + m.note else ""), style = T.Small, modifier = Modifier.weight(1f))
        Text(w.money(m.amount, sign = true), style = T.TypeS.copy(color = if (m.amount > 0) C.paper else C.smoke))
    }
}
