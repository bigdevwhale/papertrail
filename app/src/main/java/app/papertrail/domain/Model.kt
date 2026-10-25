package app.papertrail.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Who the money goes to. A [SUSPECT] can be put on a case; a [CITIZEN] (groceries, rent, the doctor) is
 * needed and never prosecuted, though a limit still watches it.
 */
enum class Role { SUSPECT, CITIZEN }

/**
 * A category, as a face in the files: [preset] names one of the house characters, otherwise [name] and
 * [alias] are the detective's own and [face] seeds the drawn portrait. [limit] is the monthly tail put on
 * it (0 = none); [banUntil] is the end of a week-long ban handed down in a verdict.
 */
@Entity(tableName = "suspect")
data class Suspect(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preset: String? = null,
    val name: String = "",
    val alias: String = "",
    val role: Role = Role.SUSPECT,
    val face: Int = 0,
    val limit: Long = 0,
    val banUntil: Long = 0,
    val hidden: Boolean = false,
    val pos: Int = 0,
)

/** What a clue is: money spent, a fee coming in, or money put into the safe (negative: taken out). */
enum class Kind { SPEND, FEE, VAULT }

enum class Source { HAND, NOTICE, IMPORT }

/**
 * One piece of evidence. Amounts are in minor units (kopecks, cents). A clue without a [suspectId] is an
 * envelope still to be sorted. [cash] marks money taken out in cash: until it's [solved] it is a cold case.
 * [billId] ties it to a known bill, which the daily advance doesn't count.
 */
@Entity(tableName = "clue", indices = [Index("at"), Index("suspectId")])
data class Clue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Kind = Kind.SPEND,
    val amount: Long,
    val at: Long,
    val suspectId: Long? = null,
    val place: String = "",
    val note: String = "",
    val source: Source = Source.HAND,
    val pkg: String? = null,
    val cash: Boolean = false,
    val solved: Boolean = false,
    val billId: Long? = null,
)

/** The patterns a case can be opened on. */
enum class Pattern { LIMIT, SPIKE, NIGHT, DRIP, WEEKDAY }

enum class Verdict { OPEN, ACQUITTED, GUILTY }

/** Why it happened, as the detective names it on closing the case. */
enum class Motive { TIRED, BORED, TREAT, SALE, COMPANY, NEEDED }

/** What a guilty verdict does: keep an eye, put a monthly tail on, or a week-long ban. */
enum class Measure { NONE, WATCH, LIMIT, BAN }

/**
 * A case on the board. The engine opens it on a [pattern] over the clues [clueIds] between [fromAt] and
 * [toAt]; [stat] is the pattern's number (late clues, the weekday, the spike in percent).
 */
@Entity(tableName = "affair", indices = [Index("verdict")])
data class Case(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val pattern: Pattern,
    val suspectId: Long,
    val fromAt: Long,
    val toAt: Long,
    val openedAt: Long,
    val sum: Long,
    val count: Int,
    val stat: Int = 0,
    val clueIds: String = "",
    val verdict: Verdict = Verdict.OPEN,
    val motive: Motive? = null,
    val measure: Measure = Measure.NONE,
    val closedAt: Long? = null,
) {
    val clues: List<Long> get() = if (clueIds.isEmpty()) emptyList() else clueIds.split(',').mapNotNull { it.toLongOrNull() }
    val open get() = verdict == Verdict.OPEN
}

/** A subscription seen coming back on the same day each month: new, cancelled, or allowed to stay. */
enum class GhostState { SEEN, EXORCISED, ALLOWED }

@Entity(tableName = "ghost")
data class Ghost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The place, normalised, that recognises the ghost when it comes back. */
    val key: String,
    val label: String,
    val amount: Long,
    val day: Int,
    val state: GhostState = GhostState.SEEN,
    val seenAt: Long,
    val lastAt: Long,
    val count: Int,
    /** Set when a ghost already exorcised was seen again. */
    val returned: Boolean = false,
)

/** A known payment the informant whispers about before it's due: rent, the phone, a loan. */
@Entity(tableName = "bill")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val day: Int,
    val suspectId: Long? = null,
    /** The period (its first day, as epoch day) this bill was last paid in. */
    val paidPeriod: Long = -1,
)

/** A ticket out of this town: a savings goal, stamped as money is set aside for it. */
@Entity(tableName = "ticket")
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val target: Long,
    val saved: Long = 0,
    val createdAt: Long,
    val doneAt: Long? = null,
    val style: Int = 0,
)

enum class Gender { HE, SHE }

/** The advance from the client: a monthly sum for living, counted from [payday]. */
data class Plan(val advance: Long, val payday: Int)

/** Everything stored, at one moment. */
data class State(
    val suspects: List<Suspect>,
    val clues: List<Clue>,
    val cases: List<Case>,
    val ghosts: List<Ghost>,
    val bills: List<Bill>,
    val tickets: List<Ticket>,
) {
    private val byId = suspects.associateBy { it.id }
    fun suspect(id: Long?) = id?.let { byId[it] }
    val files get() = suspects.filter { !it.hidden }.sortedWith(compareBy({ it.role }, { it.pos }, { it.id }))
    val envelopes get() = clues.filter { it.kind == Kind.SPEND && it.suspectId == null && !it.cash }.sortedByDescending { it.at }
    val cold get() = clues.filter { it.cash && !it.solved }.sortedByDescending { it.at }
    val openCases get() = cases.filter { it.open }.sortedByDescending { it.openedAt }
    val closedCases get() = cases.filter { !it.open }.sortedByDescending { it.closedAt ?: 0 }
    val vault get() = clues.filter { it.kind == Kind.VAULT }.sumOf { it.amount }
    val inTickets get() = tickets.filter { it.doneAt == null }.sumOf { it.saved }
    val vaultFree get() = vault - inTickets
    fun clue(id: Long) = clues.firstOrNull { it.id == id }
    fun case(id: Long) = cases.firstOrNull { it.id == id }
}

fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun Long.hour(zone: ZoneId): Int = Instant.ofEpochMilli(this).atZone(zone).hour
fun LocalDate.start(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

const val DAY = 86_400_000L
