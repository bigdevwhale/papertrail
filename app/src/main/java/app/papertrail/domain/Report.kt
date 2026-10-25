package app.papertrail.domain

import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

/** What a day was like, for the evening report. Earlier ones win. */
enum class Cond { FEE, BIG, OVER, LATE, BUSY, FIRST, LAST, QUIET, CALM }

data class Line(val cond: Cond, val ru: String, val en: String)

/** The evening report lines from assets/lines.tsv. */
class Lines(val items: List<Line>) {
    fun of(c: Cond) = items.filter { it.cond == c }

    companion object {
        fun read(input: InputStream): Lines = Lines(input.bufferedReader(Charsets.UTF_8).readLines().mapNotNull { l ->
            if (l.isBlank() || l.startsWith("#")) return@mapNotNull null
            val p = l.split('\t')
            val c = Cond.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
            if (p.size < 3) null else Line(c, p[1], p[2])
        })
    }
}

/** A day's facts, as the report sees them. */
data class DayFacts(
    val date: LocalDate,
    val cond: Cond,
    val count: Int,
    val sum: Long,
    /** The suspect who took the most that day. */
    val who: Long?,
    val fee: Long,
)

object Report {
    fun facts(s: State, plan: Plan, day: LocalDate, zone: ZoneId): DayFacts {
        val clues = s.clues.filter { it.at.date(zone) == day }
        val spends = clues.filter { it.kind == Kind.SPEND }
        val variable = spends.filter { it.billId == null }
        val fee = clues.filter { it.kind == Kind.FEE }.sumOf { it.amount }
        val t = Ledger.today(plan, s.clues, s.bills, day.start(zone) + DAY / 2, zone)
        val sum = variable.sumOf { it.amount }
        val who = variable.filter { it.suspectId != null }.groupBy { it.suspectId!! }.maxByOrNull { (_, v) -> v.sumOf { it.amount } }?.key
        val cond = when {
            fee > 0 && sum <= t.perDay -> Cond.FEE
            variable.any { it.amount > maxOf(t.perDay * 3, 1) } -> Cond.BIG
            sum > t.perDay -> Cond.OVER
            variable.any { Detective.late(it.at.hour(zone)) } -> Cond.LATE
            variable.size >= 5 -> Cond.BUSY
            day == t.period.start -> Cond.FIRST
            t.daysLeft <= 3 -> Cond.LAST
            variable.isEmpty() -> Cond.QUIET
            else -> Cond.CALM
        }
        return DayFacts(day, cond, variable.size, sum, who, fee)
    }

    /** The same day always gets the same line; neighbouring days get different ones. */
    fun pick(lines: Lines, f: DayFacts): Line? {
        val pool = lines.of(f.cond).ifEmpty { lines.of(Cond.CALM) }
        if (pool.isEmpty()) return null
        val h = (f.date.toEpochDay() * 2654435761L).ushr(7)
        return pool[(h % pool.size).toInt()]
    }
}
