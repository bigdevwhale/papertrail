package app.papertrail.data

import app.papertrail.domain.Bill
import app.papertrail.domain.Case
import app.papertrail.domain.Cast
import app.papertrail.domain.Clue
import app.papertrail.domain.DAY
import app.papertrail.domain.Detective
import app.papertrail.domain.Ghost
import app.papertrail.domain.GhostState
import app.papertrail.domain.Ghosts
import app.papertrail.domain.Kind
import app.papertrail.domain.Ledger
import app.papertrail.domain.Measure
import app.papertrail.domain.Motive
import app.papertrail.domain.Notice
import app.papertrail.domain.Plan
import app.papertrail.domain.Role
import app.papertrail.domain.Source
import app.papertrail.domain.State
import app.papertrail.domain.Suspect
import app.papertrail.domain.Ticket
import app.papertrail.domain.Verdict
import app.papertrail.domain.date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * The office's files as stored. Every change reads the state, writes, and publishes the new state to
 * [state]; [investigate] is the detective going over it all for new cases and ghosts.
 */
class Repo(private val db: TrailDb) {
    private val dao = db.dao()
    private val mutex = Mutex()
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    /** The clock; the demo seed winds it to replay months. */
    var clock: () -> Long = System::currentTimeMillis
    var zone: ZoneId = ZoneId.systemDefault()

    suspend fun refresh(): State =
        State(dao.suspects(), dao.clues(), dao.cases(), dao.ghosts(), dao.bills(), dao.tickets()).also { _state.value = it }

    private suspend fun <T> op(block: suspend (State, Long) -> T): T = mutex.withLock {
        val s = refresh()
        block(s, clock()).also { refresh() }
    }

    // ---- the files

    /** The first day: the chosen house characters go into the files. */
    suspend fun hire(keys: List<String>) = op { s, _ ->
        val have = s.suspects.mapNotNull { it.preset }.toSet()
        val add = keys.filter { it !in have }.mapNotNull { Cast[it] }.mapIndexed { i, c ->
            Suspect(preset = c.key, role = c.role, face = c.key.hashCode(), pos = s.suspects.size + i)
        }
        if (add.isNotEmpty()) dao.insertSuspects(add)
        for (sus in s.suspects.filter { it.preset != null }) {
            val want = sus.preset in keys
            if (sus.hidden == want) dao.update(sus.copy(hidden = !want))
        }
    }

    suspend fun addSuspect(name: String, alias: String, role: Role): Long = op { s, now ->
        dao.insert(Suspect(name = name.trim(), alias = alias.trim(), role = role, face = (now % 100_000).toInt(), pos = s.suspects.size))
    }

    suspend fun saveSuspect(sus: Suspect) = op { _, _ -> dao.update(sus) }

    // ---- clues

    /** A clue by hand; returns its id. A place seen before goes to the same suspect if none is named. */
    suspend fun add(c: Clue): Long = op { s, now ->
        val sus = c.suspectId ?: learned(s, c.place)
        val id = dao.insert(c.copy(suspectId = if (c.kind == Kind.SPEND && !c.cash) sus else c.suspectId))
        visit(s, c.copy(id = id))
        id
    }

    suspend fun save(c: Clue) = op { _, _ -> dao.update(c) }
    suspend fun delete(c: Clue) = op { _, _ -> dao.delete(c) }

    /** An envelope sorted: the clue gets its suspect. */
    suspend fun sort(id: Long, suspectId: Long) = op { s, _ ->
        s.clue(id)?.let { dao.update(it.copy(suspectId = suspectId)) }
    }

    /** A cold case solved: the cash is put down to a suspect at last. */
    suspend fun solve(id: Long, suspectId: Long?) = op { s, _ ->
        s.clue(id)?.let { dao.update(it.copy(solved = true, suspectId = suspectId)) }
    }

    /**
     * A bank's notification. Returns the new clue, or null when it wasn't money or was the same
     * notification again (banks repost, and some send the push and an SMS).
     */
    suspend fun notice(pkg: String, title: String, text: String): Clue? = op { s, now ->
        val r = Notice.read(title, text) ?: return@op null
        if (dao.recent(pkg, r.amount, now - 3 * 60_000) > 0) return@op null
        val c = Clue(
            kind = r.kind, amount = r.amount, at = now, place = r.place, source = Source.NOTICE, pkg = pkg, cash = r.cash,
            suspectId = if (r.kind == Kind.SPEND && !r.cash) learned(s, r.place) else null,
        )
        val id = dao.insert(c)
        visit(s, c.copy(id = id))
        c.copy(id = id)
    }

    /** The suspect a place went to the last two times, if both agree. */
    private fun learned(s: State, place: String): Long? {
        val key = Notice.key(place).ifEmpty { return null }
        val past = s.clues.filter { it.suspectId != null && Notice.key(it.place) == key }.takeLast(2)
        return past.map { it.suspectId }.distinct().singleOrNull()?.takeIf { past.size == 2 && s.suspect(it)?.hidden == false }
    }

    /** Who to offer for an envelope: what this place went to before, else a house character's hint. */
    fun suggest(s: State, place: String): Suspect? {
        val key = Notice.key(place)
        val past = if (key.isEmpty()) null else s.clues.lastOrNull { it.suspectId != null && Notice.key(it.place) == key }?.suspectId
        return s.suspect(past)?.takeIf { !it.hidden } ?: Cast.guess(place, s.files)
    }

    private suspend fun visit(s: State, c: Clue) {
        if (c.kind != Kind.SPEND) return
        val key = Notice.key(c.place).ifEmpty { return }
        s.ghosts.firstOrNull { it.key == key }?.let { g -> Ghosts.visit(g, c).takeIf { it != g }?.let { dao.update(it) } }
    }

    // ---- the detective at work

    /** New cases and new ghosts, after any change worth a look. Returns the cases opened. */
    suspend fun investigate(plan: Plan): List<Case> = op { s, now ->
        if (plan.advance <= 0) return@op emptyList()
        val ghosts = Ghosts.find(s.clues, s.ghosts, now, zone)
        if (ghosts.isNotEmpty()) dao.insertGhosts(ghosts)
        val cases = Detective.open(s, plan, now, zone)
        if (cases.isNotEmpty()) dao.insertCases(cases)
        cases
    }

    suspend fun verdict(id: Long, v: Verdict, motive: Motive?, measure: Measure, limit: Long = 0) = op { s, now ->
        val c = s.case(id) ?: return@op
        dao.update(c.copy(verdict = v, motive = motive, measure = if (v == Verdict.GUILTY) measure else Measure.NONE, closedAt = now))
        val sus = s.suspect(c.suspectId) ?: return@op
        if (v != Verdict.GUILTY) return@op
        when (measure) {
            Measure.LIMIT -> if (limit > 0) dao.update(sus.copy(limit = limit))
            Measure.BAN -> dao.update(sus.copy(banUntil = now + 7 * DAY))
            else -> Unit
        }
    }

    suspend fun ghost(id: Long, state: GhostState) = op { s, _ ->
        s.ghosts.firstOrNull { it.id == id }?.let { dao.update(it.copy(state = state, returned = false)) }
    }

    // ---- bills

    suspend fun saveBill(b: Bill) = op { _, _ -> if (b.id == 0L) dao.insert(b) else dao.update(b) }
    suspend fun deleteBill(b: Bill) = op { _, _ -> dao.delete(b) }

    /** A known bill paid: the clue goes in, and the bill isn't held back any more this period. */
    suspend fun pay(id: Long, plan: Plan) = op { s, now ->
        val b = s.bills.firstOrNull { it.id == id } ?: return@op
        val p = Ledger.period(now.date(zone), plan.payday)
        dao.insert(Clue(kind = Kind.SPEND, amount = b.amount, at = now, suspectId = b.suspectId, place = b.name, billId = b.id))
        dao.update(b.copy(paidPeriod = p.id))
    }

    // ---- the safe

    /** Money into the safe (positive) or out of it (negative). */
    suspend fun safe(amount: Long, note: String = "") = op { _, now ->
        if (amount != 0L) dao.insert(Clue(kind = Kind.VAULT, amount = amount, at = now, note = note))
    }

    suspend fun saveTicket(t: Ticket) = op { _, now -> if (t.id == 0L) dao.insert(t.copy(createdAt = now)) else dao.update(t) }
    suspend fun deleteTicket(t: Ticket) = op { _, _ -> dao.delete(t) }

    /** A stamp on a ticket: free money in the safe set aside for it. */
    suspend fun stamp(id: Long, amount: Long): Boolean = op { s, _ ->
        val t = s.tickets.firstOrNull { it.id == id } ?: return@op false
        val a = minOf(amount, s.vaultFree, t.target - t.saved)
        if (a <= 0) return@op false
        dao.update(t.copy(saved = t.saved + a)); true
    }

    /** The ticket used: its money leaves the safe and the ticket goes in the album. */
    suspend fun redeem(id: Long) = op { s, now ->
        val t = s.tickets.firstOrNull { it.id == id } ?: return@op
        dao.insert(Clue(kind = Kind.VAULT, amount = -t.saved, at = now, note = t.title))
        dao.update(t.copy(doneAt = now))
    }

    /** Raw writes for the demo seed. */
    suspend fun raw(block: suspend (TrailDao) -> Unit) = mutex.withLock { block(dao); refresh() }
}
