package app.papertrail.domain

import java.time.ZoneId

/** What the front page leads with. */
enum class Lead { CAUGHT, AT_LARGE, SAVED, OVERSPENT, QUIET_MONTH, FIRST_ISSUE }

/** A period as the city paper sees it: the money, the suspects that rose and fell, the cases. */
data class Issue(
    val period: Period,
    val number: Int,
    val advance: Long,
    val spent: Long,
    val fees: Long,
    val intoSafe: Long,
    /** Suspect id to spending this period, and in the one before. */
    val now: Map<Long, Long>,
    val before: Map<Long, Long>,
    val lead: Lead,
    /** The suspect the headline is about, if it's about one. */
    val star: Long?,
    /** The change for [star], in percent. */
    val change: Int,
    val closed: List<Case>,
    val opened: List<Case>,
    val ghosts: List<Ghost>,
    val quietDays: Int,
    val days: Int,
    val final: Boolean,
) {
    val left get() = advance - spent
    val top get() = now.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
}

object Gazette {
    fun issue(s: State, plan: Plan, period: Period, now: Long, zone: ZoneId, firstPeriod: Period): Issue {
        val prev = period.prev(plan.payday)
        val cur = Ledger.bySuspect(s.clues, period, zone)
        val old = Ledger.bySuspect(s.clues, prev, zone)
        val inP = Ledger.inPeriod(s.clues, period, zone)
        val spent = inP.filter { it.kind == Kind.SPEND }.sumOf { it.amount }
        val today = now.date(zone)
        val final = !period.end.isAfter(today)
        val days = if (final) period.days else (today.toEpochDay() - period.start.toEpochDay() + 1).toInt()
        val spendDays = inP.filter { it.kind == Kind.SPEND && it.billId == null }.map { it.at.date(zone) }.toSet()
        val suspects = s.suspects.filter { it.role == Role.SUSPECT }.map { it.id }.toSet()
        // The suspect with the biggest fall (caught) or rise (at large), among those with a real history.
        val moves = (cur.keys + old.keys).filter { it in suspects }.mapNotNull { id ->
            val a = old[id] ?: 0; val b = cur[id] ?: 0
            if (a < plan.advance / 50) null else id to ((b - a) * 100 / a).toInt()
        }
        val fall = moves.minByOrNull { it.second }?.takeIf { it.second <= -25 }
        val rise = moves.maxByOrNull { it.second }?.takeIf { it.second >= 40 }
        val first = period.id <= firstPeriod.id
        val (lead, star, change) = when {
            first && old.isEmpty() -> Triple(Lead.FIRST_ISSUE, null, 0)
            fall != null && (rise == null || -fall.second >= rise.second) -> Triple(Lead.CAUGHT, fall.first, fall.second)
            rise != null -> Triple(Lead.AT_LARGE, rise.first, rise.second)
            final && spent > plan.advance -> Triple(Lead.OVERSPENT, null, 0)
            spent <= plan.advance * days / maxOf(period.days, 1) -> Triple(Lead.SAVED, null, 0)
            else -> Triple(Lead.QUIET_MONTH, null, 0)
        }
        val from = period.start.start(zone); val to = period.end.start(zone)
        return Issue(
            period = period,
            number = generateSequence(firstPeriod) { it.next(plan.payday) }.takeWhile { it.id <= period.id }.count().coerceAtLeast(1),
            advance = plan.advance, spent = spent,
            fees = inP.filter { it.kind == Kind.FEE }.sumOf { it.amount },
            intoSafe = inP.filter { it.kind == Kind.VAULT }.sumOf { it.amount },
            now = cur, before = old, lead = lead, star = star, change = change,
            closed = s.cases.filter { !it.open && (it.closedAt ?: 0) in from until to },
            opened = s.cases.filter { it.openedAt in from until to },
            ghosts = s.ghosts.filter { it.lastAt in from until to },
            quietDays = (0 until days).count { period.start.plusDays(it.toLong()) !in spendDays },
            days = days, final = final,
        )
    }

    /** The first period with any clue in it: the paper's first issue. */
    fun firstPeriod(s: State, plan: Plan, now: Long, zone: ZoneId): Period =
        Ledger.period((s.clues.minOfOrNull { it.at } ?: now).date(zone), plan.payday)
}
