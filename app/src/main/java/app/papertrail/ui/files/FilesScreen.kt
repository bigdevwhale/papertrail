package app.papertrail.ui.files

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Bill
import app.papertrail.domain.GhostState
import app.papertrail.domain.Ledger
import app.papertrail.domain.Notice
import app.papertrail.domain.Role
import app.papertrail.domain.Suspect
import app.papertrail.sound.Snd
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
import app.papertrail.ui.components.Portrait
import app.papertrail.ui.components.Slab
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.TopBar
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

@Composable
fun FilesScreen(h: Home, nav: Nav) {
    val w = h.w
    val spent = Ledger.bySuspect(h.s.clues, h.today.period, h.zone)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(w.t("Картотека", "The files"), w.t("за период с ${w.date(h.today.period.start)}", "this period, since ${w.date(h.today.period.start)}"))
        Column(Modifier.padding(horizontal = 16.dp)) {
            for (role in Role.entries) {
                val list = h.s.files.filter { it.role == role }
                if (list.isEmpty()) continue
                Label(if (role == Role.SUSPECT) w.t("Подозреваемые", "Suspects") else w.t("Честные граждане", "Honest citizens"))
                for (row in list.chunked(3)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (s in row) Card(h, s, spent[s.id] ?: 0, Modifier.weight(1f)) { nav.push(Overlay.Dossier(s.id)) }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            NewSuspect(h)
            Ghosts(h)
            Bills(h)
            Gap(24)
        }
    }
}

@Composable
private fun Card(h: Home, s: Suspect, spent: Long, modifier: Modifier, onClick: () -> Unit) {
    val w = h.w
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            Portrait(s, Modifier.fillMaxWidth())
            if (s.banUntil > h.now) Stamp(w.t("Запрет", "Banned"), Modifier.align(Alignment.Center), angle = -14f, small = true)
            if (h.s.openCases.any { it.suspectId == s.id }) Stamp(w.t("В розыске", "Wanted"), Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 2.dp), angle = 8f, small = true)
        }
        Text(w.alias(s), style = T.TypeS.copy(color = C.paper), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(w.money(spent), style = T.TypeS.copy(color = if (s.limit in 1 until spent) C.red else C.smoke))
        if (s.limit > 0) {
            Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).background(C.line)) {
                Box(Modifier.fillMaxWidth((spent.toFloat() / s.limit).coerceIn(0f, 1f)).height(3.dp).background(if (spent > s.limit) C.red else C.fog))
            }
        }
    }
}

/** A suspect of the detective's own: a name for the category and, if wanted, a nickname. */
@Composable
private fun NewSuspect(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.SUSPECT) }
    if (!open) {
        Btn(w.t("+ Новое лицо", "+ New face"), Modifier.fillMaxWidth(), look = Look.GHOST) { open = true }
        return
    }
    Slab {
        Text(w.t("Новое лицо в картотеке", "A new face in the files"), style = T.Heading)
        Gap(8)
        Field(name, { name = it }, w.t("что это: «книги», «подарки»…", "what it is: books, gifts…"), max = 30)
        Gap(6)
        Field(alias, { alias = it }, w.t("кличка: «Букинист»", "nickname: The Bookseller"), max = 24)
        Gap(8)
        Flow {
            Chip(w.role(Role.SUSPECT), role == Role.SUSPECT) { role = Role.SUSPECT }
            Chip(w.role(Role.CITIZEN), role == Role.CITIZEN) { role = Role.CITIZEN }
        }
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(w.t("Отмена", "Cancel"), Modifier.weight(1f), look = Look.GHOST) { open = false }
            Btn(w.t("Завести", "File"), Modifier.weight(1f), enabled = name.isNotBlank()) {
                c.sfx.play(Snd.KEY)
                val n = name; val a = alias.ifBlank { name }; val r = role
                c.write { repo.addSuspect(n, a, r) }
                name = ""; alias = ""; open = false
            }
        }
    }
}

/** Subscriptions: the same sum coming back every month. Exorcise one, or let it stay. */
@Composable
private fun Ghosts(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    val ghosts = h.s.ghosts.sortedWith(compareBy({ it.state != GhostState.SEEN && !it.returned }, { -it.amount }))
    Label(w.t("Призраки", "Ghosts"))
    if (ghosts.isEmpty()) {
        Text(w.t("Призраков пока не видно. Они появятся, если одна и та же сумма будет приходить из одного места каждый месяц.", "No ghosts seen yet. They show up when the same sum comes from the same place every month."), style = T.Small)
        return
    }
    val active = ghosts.filter { it.state != GhostState.EXORCISED }
    if (active.isNotEmpty()) Text(
        w.t("Призраки съедают ${w.money(active.sumOf { it.amount } * 12)} в год.", "The ghosts eat ${w.money(active.sumOf { it.amount } * 12)} a year."),
        style = T.Small, modifier = Modifier.padding(bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (g in ghosts) Slab {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.label, style = T.Body)
                    Text(w.money(g.amount) + w.t(" · каждое ${g.day}-е · ", " · on the ${g.day}th · ") + w.ghost(g.state) + if (g.returned) w.t(" · вернулся!", " · came back!") else "", style = T.Small.copy(color = if (g.returned) C.red else C.smoke))
                }
            }
            if (g.state == GhostState.SEEN || g.returned) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(w.t("Изгнать", "Exorcise"), Modifier.weight(1f)) { c.sfx.play(Snd.STAMP); c.write { repo.ghost(g.id, GhostState.EXORCISED) } }
                Btn(w.t("Пусть живёт", "Let it stay"), Modifier.weight(1f), look = Look.GHOST) { c.write { repo.ghost(g.id, GhostState.ALLOWED) } }
            }
        }
    }
    Text(w.t("«Изгнать» — значит, вы отменили подписку. Если призрак явится снова, осведомитель сообщит.", "«Exorcise» means you cancelled it. If the ghost comes back, the informant will tell you."), style = T.Small, modifier = Modifier.padding(top = 8.dp))
}

/** Known bills: kept back from the daily advance until paid. */
@Composable
private fun Bills(h: Home) {
    val w = h.w
    val c = LocalContext.current.container
    var edit by remember { mutableStateOf<Bill?>(null) }
    Label(w.t("Счета по расписанию", "Scheduled bills"))
    Text(w.t("Аренда, связь, кредит. Их сумма заранее откладывается из аванса, а осведомитель напомнит за пять дней.", "Rent, phone, a loan. Their sum is set aside from the advance, and the informant reminds you five days ahead."), style = T.Small, modifier = Modifier.padding(bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in h.s.bills) Slab(onClick = { edit = b }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = T.Body)
                    Text(w.money(b.amount) + w.t(" · ${b.day}-го числа", " · on the ${b.day}th"), style = T.Small)
                }
                if (b.paidPeriod == h.today.period.id) Stamp(w.t("Оплачено", "Paid"), color = C.fog, angle = -5f)
                else Text(w.t("ОПЛАТИТЬ", "PAY"), style = T.Button.copy(color = C.red), modifier = Modifier.clickable {
                    c.sfx.play(Snd.STAMP)
                    val plan = h.settings.plan
                    c.write { repo.pay(b.id, plan) }
                }.padding(8.dp))
            }
        }
    }
    Gap(8)
    val e = edit
    if (e == null) Btn(w.t("+ Счёт", "+ Bill"), Modifier.fillMaxWidth(), look = Look.GHOST) { edit = Bill(name = "", amount = 0, day = 1) }
    else BillForm(h, e) { edit = null }
}

@Composable
private fun BillForm(h: Home, b: Bill, done: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    var name by remember(b) { mutableStateOf(b.name) }
    var amount by remember(b) { mutableStateOf(if (b.amount > 0) (b.amount / 100).toString() else "") }
    var day by remember(b) { mutableStateOf(b.day.toString()) }
    var who by remember(b) { mutableStateOf(b.suspectId) }
    Slab {
        Field(name, { name = it }, w.t("что: «Аренда»", "what: Rent"), max = 30)
        Gap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(amount, { amount = it.filter { ch -> ch.isDigit() } }, w.t("сумма", "amount"), Modifier.weight(2f), number = true)
            Field(day, { day = it.filter { ch -> ch.isDigit() }.take(2) }, w.t("число", "day"), Modifier.weight(1f), number = true)
        }
        Gap(8)
        Flow { for (s in h.s.files.filter { it.role == Role.CITIZEN }) Chip(w.alias(s), who == s.id) { who = if (who == s.id) null else s.id } }
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (b.id != 0L) Btn(w.t("Удалить", "Delete"), Modifier.weight(1f), look = Look.GHOST) { c.write { repo.deleteBill(b) }; done() }
            val a = Notice.minor(amount) ?: 0
            val d = day.toIntOrNull()?.coerceIn(1, 31) ?: 0
            Btn(w.t("Готово", "Done"), Modifier.weight(1f), enabled = name.isNotBlank() && a > 0 && d > 0) {
                val nb = b.copy(name = name.trim(), amount = a, day = d, suspectId = who)
                c.write { repo.saveBill(nb) }
                done()
            }
        }
    }
}
