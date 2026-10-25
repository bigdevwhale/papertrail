package app.papertrail.domain

import java.time.LocalDate
import java.time.ZoneId

/** One advance period: from a payday up to (not including) the next. */
data class Period(val start: LocalDate, val end: LocalDate) {
    val id: Long get() = start.toEpochDay()
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt()
    fun has(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
    fun prev(payday: Int) = Ledger.period(start.minusDays(1), payday)
    fun next(payday: Int) = Ledger.period(end, payday)
}

/** Today's matchbook: what may still go today and what's left until the next fee. */
data class Today(
    val period: Period,
    val perDay: Long,
    val spentToday: Long,
    val leftToday: Long,
    /** What's left of the advance after known bills, from today on, before today's spending. */
    val left: Long,
    val daysLeft: Int,
    /** Bills not yet paid this period, kept back. */
    val reserved: Long,
    val spent: Long,
)

object Ledger {
    /** The period a day falls in; a payday past the month's last day falls on that last day. */
    fun period(d: LocalDate, payday: Int): Period {
        fun pay(m: LocalDate) = m.withDayOfMonth(minOf(payday, m.lengthOfMonth()))
        var start = pay(d.withDayOfMonth(1))
        if (start.isAfter(d)) start = pay(d.withDayOfMonth(1).minusMonths(1))
        val end = pay(start.withDayOfMonth(1).plusMonths(1))
        return Period(start, end)
    }

    /** Money out of the advance: spending, and money put into the safe (taking it out puts it back). */
    private fun out(c: Clue) = when (c.kind) { Kind.SPEND -> c.amount; Kind.VAULT -> c.amount; Kind.FEE -> 0 }

    fun inPeriod(clues: List<Clue>, p: Period, zone: ZoneId) = clues.filter { p.has(it.at.date(zone)) }

    /** Everything spent in a period, bills included, the safe not. */
    fun spent(clues: List<Clue>, p: Period, zone: ZoneId) = inPeriod(clues, p, zone).filter { it.kind == Kind.SPEND }.sumOf { it.amount }

    fun today(plan: Plan, clues: List<Clue>, bills: List<Bill>, now: Long, zone: ZoneId): Today {
        val d = now.date(zone)
        val p = period(d, plan.payday)
        val mine = inPeriod(clues, p, zone)
        val fixed = mine.filter { it.billId != null }.sumOf { out(it) }
        val reserved = bills.filter { it.paidPeriod != p.id }.sumOf { it.amount }
        val variable = mine.filter { it.billId == null }
        val before = variable.filter { it.at.date(zone).isBefore(d) }.sumOf { out(it) }
        val todaySpent = variable.filter { it.at.date(zone) == d }.sumOf { out(it) }
        val left = plan.advance - fixed - reserved - before
        val daysLeft = (p.end.toEpochDay() - d.toEpochDay()).toInt().coerceAtLeast(1)
        val perDay = left.coerceAtLeast(0) / daysLeft
        return Today(p, perDay, todaySpent, perDay - todaySpent, left, daysLeft, reserved, mine.filter { it.kind == Kind.SPEND }.sumOf { it.amount })
    }

    /** Spending per suspect in a period. */
    fun bySuspect(clues: List<Clue>, p: Period, zone: ZoneId): Map<Long, Long> =
        inPeriod(clues, p, zone).filter { it.kind == Kind.SPEND && it.suspectId != null }.groupBy { it.suspectId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }

    /** Bills whose day is coming within [days] and not yet paid. */
    fun dueSoon(bills: List<Bill>, p: Period, today: LocalDate, days: Int = 5): List<Pair<Bill, LocalDate>> =
        bills.filter { it.paidPeriod != p.id }.map { b ->
            var due = p.start.withDayOfMonth(minOf(b.day, p.start.lengthOfMonth()))
            if (due.isBefore(p.start)) due = p.start.plusMonths(1).let { it.withDayOfMonth(minOf(b.day, it.lengthOfMonth())) }
            b to due
        }.filter { (_, due) -> due.toEpochDay() - today.toEpochDay() <= days }.sortedBy { it.second }
}
