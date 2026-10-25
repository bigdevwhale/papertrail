package app.papertrail.domain

import java.time.ZoneId

/**
 * The detective's nose: reads the clues and opens a case when one suspect's habits show a pattern.
 * One open case per suspect, three on the board at most, and a suspect just tried gets a week of peace.
 */
object Detective {
    const val MAX_OPEN = 3
    /** The same pattern on the same suspect doesn't come back for three weeks. */
    const val SAME_COOLDOWN = 21 * DAY
    const val ANY_COOLDOWN = 7 * DAY

    /** Late means from ten at night till five in the morning. */
    fun late(h: Int) = h >= 22 || h < 5

    data class Lead(val pattern: Pattern, val suspectId: Long, val from: Long, val to: Long, val clues: List<Clue>, val stat: Int) {
        val sum get() = clues.sumOf { it.amount }
    }

    fun leads(s: State, plan: Plan, now: Long, zone: ZoneId): List<Lead> {
        val spends = s.clues.filter { it.kind == Kind.SPEND && it.suspectId != null && it.billId == null && it.at <= now }
        val bySuspect = spends.groupBy { it.suspectId!! }
        val p = Ledger.period(now.date(zone), plan.payday)
        val out = mutableListOf<Lead>()
        for (sus in s.suspects.filter { !it.hidden }) {
            val mine = bySuspect[sus.id].orEmpty()
            // A tail crossed: any suspect, citizens too.
            if (sus.limit > 0) {
                val inP = mine.filter { p.has(it.at.date(zone)) }
                val sum = inP.sumOf { it.amount }
                if (sum > sus.limit) out += Lead(Pattern.LIMIT, sus.id, p.start.start(zone), now, inP, (sum * 100 / sus.limit).toInt())
            }
            if (sus.role == Role.CITIZEN) continue

            val week = mine.filter { it.at > now - 7 * DAY }
            val past = mine.filter { it.at in (now - 63 * DAY)..(now - 7 * DAY) }
            val first = mine.minOfOrNull { it.at } ?: now
            val weeks = ((now - 7 * DAY - maxOf(first, now - 63 * DAY)) / (7 * DAY)).toInt()
            if (weeks >= 3 && week.size >= 2) {
                val base = past.sumOf { it.amount } / weeks
                val sum = week.sumOf { it.amount }
                if (sum > base * 16 / 10 && sum - base > plan.advance * 3 / 100)
                    out += Lead(Pattern.SPIKE, sus.id, now - 7 * DAY, now, week, if (base > 0) ((sum - base) * 100 / base).toInt() else 100)
            }

            val fortnight = mine.filter { it.at > now - 14 * DAY }
            val night = fortnight.filter { late(it.at.hour(zone)) }
            if (night.size >= 3) out += Lead(Pattern.NIGHT, sus.id, now - 14 * DAY, now, night, night.size)

            if (fortnight.size >= 7) {
                val median = fortnight.map { it.amount }.sorted()[fortnight.size / 2]
                if (median <= maxOf(plan.advance / 100, 1)) out += Lead(Pattern.DRIP, sus.id, now - 14 * DAY, now, fortnight, fortnight.size)
            }

            val month = mine.filter { it.at > now - 28 * DAY }
            if (month.size >= 5) {
                val (dow, hits) = month.groupBy { it.at.date(zone).dayOfWeek.value }.maxBy { it.value.size }.let { it.key to it.value }
                if (hits.size >= 4 && hits.size * 10 >= month.size * 6) out += Lead(Pattern.WEEKDAY, sus.id, now - 28 * DAY, now, hits, dow)
            }
        }
        return out
    }

    /** The leads worth a case now: priority by pattern, one per suspect, within the board's room and the cooldowns. */
    fun open(s: State, plan: Plan, now: Long, zone: ZoneId): List<Case> {
        val room = MAX_OPEN - s.openCases.size
        if (room <= 0) return emptyList()
        val p = Ledger.period(now.date(zone), plan.payday)
        var number = (s.cases.maxOfOrNull { it.number } ?: 0)
        val busy = s.openCases.map { it.suspectId }.toMutableSet()
        val picked = mutableListOf<Case>()
        for (l in leads(s, plan, now, zone).sortedBy { it.pattern.ordinal }) {
            if (picked.size >= room || l.suspectId in busy) continue
            val before = s.cases.filter { it.suspectId == l.suspectId }
            if (l.pattern == Pattern.LIMIT && before.any { it.pattern == Pattern.LIMIT && it.fromAt >= p.start.start(zone) }) continue
            if (l.pattern != Pattern.LIMIT && before.any { it.pattern == l.pattern && now - it.openedAt < SAME_COOLDOWN }) continue
            if (before.any { !it.open && now - (it.closedAt ?: 0) < ANY_COOLDOWN }) continue
            busy += l.suspectId
            picked += Case(
                number = ++number, pattern = l.pattern, suspectId = l.suspectId, fromAt = l.from, toAt = l.to, openedAt = now,
                sum = l.sum, count = l.clues.size, stat = l.stat, clueIds = l.clues.sortedBy { it.at }.joinToString(",") { it.id.toString() },
            )
        }
        return picked
    }

    /** What the pinned clues share, for the threads on the board: indices of clues tied to each other. */
    fun threads(case: Case, clues: List<Clue>, zone: ZoneId): List<Pair<Int, Int>> {
        if (clues.size < 2) return emptyList()
        val key: (Clue) -> Any = when (case.pattern) {
            Pattern.NIGHT -> { _ -> 0 }
            Pattern.WEEKDAY -> { c -> c.at.date(zone).dayOfWeek }
            else -> { c -> c.place.lowercase().ifEmpty { c.at.date(zone).dayOfWeek } }
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        for ((_, idx) in clues.indices.groupBy { key(clues[it]) }) for (k in 1 until idx.size) pairs += idx[k - 1] to idx[k]
        return pairs
    }

    /** The detective's rank, earned by work done, never by spending less. */
    fun points(s: State): Int =
        s.closedCases.size * 10 + s.clues.count { it.cash && it.solved } * 5 + s.ghosts.count { it.state != GhostState.SEEN } * 5 + s.tickets.count { it.doneAt != null } * 20

    val RANKS = intArrayOf(0, 30, 80, 160, 300, 500)
    fun rank(points: Int) = RANKS.indexOfLast { points >= it }
    fun nextAt(points: Int) = RANKS.firstOrNull { it > points }
}
