package app.papertrail

import app.papertrail.domain.Bill
import app.papertrail.domain.Case
import app.papertrail.domain.Clue
import app.papertrail.domain.Cond
import app.papertrail.domain.DAY
import app.papertrail.domain.Detective
import app.papertrail.domain.Ghosts
import app.papertrail.domain.Kind
import app.papertrail.domain.Ledger
import app.papertrail.domain.Lines
import app.papertrail.domain.Notice
import app.papertrail.domain.Pattern
import app.papertrail.domain.Plan
import app.papertrail.domain.Report
import app.papertrail.domain.Role
import app.papertrail.domain.State
import app.papertrail.domain.Suspect
import app.papertrail.domain.Verdict
import app.papertrail.domain.start
import app.papertrail.ui.Words
import app.papertrail.domain.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 25)
    private fun at(d: LocalDate, h: Int = 12) = d.start(zone) + h * 3_600_000L
    private val now = at(today, 20)
    private val plan = Plan(advance = 9_000_000, payday = 5)
    private val courier = Suspect(id = 1, preset = "courier")
    private val grocer = Suspect(id = 2, preset = "grocer", role = Role.CITIZEN)
    private val barista = Suspect(id = 3, preset = "barista")
    private var seq = 0L
    private fun clue(d: LocalDate, h: Int, who: Long?, rub: Int, place: String = "", kind: Kind = Kind.SPEND) =
        Clue(id = ++seq, kind = kind, amount = rub * 100L, at = at(d, h), suspectId = who, place = place)
    private fun state(clues: List<Clue>, cases: List<Case> = emptyList(), bills: List<Bill> = emptyList(), sus: List<Suspect> = listOf(courier, grocer, barista)) =
        State(sus, clues, cases, emptyList(), bills, emptyList())

    // ---- the informant

    @Test fun readsSberStyle() {
        val r = Notice.read("900", "ECMC1234 25.10 21:40 Покупка 612р Пятёрочка Баланс: 12 345.67р")!!
        assertEquals(Kind.SPEND, r.kind); assertEquals(61200, r.amount); assertEquals("Пятёрочка", r.place)
    }

    @Test fun readsTinkoffStyle() {
        val r = Notice.read("Т-Банк", "Покупка на 1 240,50 ₽, KOFEMANIYA. Карта *4821. Доступно 48 012,00 ₽")!!
        assertEquals(124050, r.amount); assertEquals("KOFEMANIYA", r.place)
    }

    @Test fun readsEnglish() {
        val r = Notice.read("Chase", "You made a purchase of \$12.40 at Blue Bottle Coffee with card ending 1234")!!
        assertEquals(1240, r.amount); assertTrue(r.place, r.place.startsWith("Blue Bottle"))
    }

    @Test fun readsIncomeAndCash() {
        val fee = Notice.read("Сбербанк", "Зачисление зарплаты 117 000р. Баланс: 130 000р")!!
        assertEquals(Kind.FEE, fee.kind); assertEquals(11_700_000, fee.amount)
        val cash = Notice.read("Сбербанк", "Выдача наличных 5000р ATM 123456 Баланс: 8 000р")!!
        assertTrue(cash.cash); assertEquals(500_000, cash.amount)
    }

    @Test fun ignoresChatter() {
        assertNull(Notice.read("Мама", "Купи хлеба, пожалуйста, и молока"))
        assertNull(Notice.read("Магазин", "Скидки до 50% на всё! Только сегодня"))
        assertNull(Notice.read("Банк", "Ваш баланс 12 000р"))
    }

    @Test fun amounts() {
        assertEquals(1234560L, Notice.minor("12 345,60"))
        assertEquals(61200L, Notice.minor("612"))
        assertEquals(1240L, Notice.minor("12.4"))
    }

    // ---- the advance

    @Test fun periods() {
        val p = Ledger.period(today, 5)
        assertEquals(LocalDate.of(2026, 10, 5), p.start); assertEquals(LocalDate.of(2026, 11, 5), p.end)
        val q = Ledger.period(LocalDate.of(2026, 10, 3), 5)
        assertEquals(LocalDate.of(2026, 9, 5), q.start)
        // A payday past a short month's end falls on its last day.
        val f = Ledger.period(LocalDate.of(2027, 2, 28), 31)
        assertEquals(LocalDate.of(2027, 2, 28), f.start); assertEquals(LocalDate.of(2027, 3, 31), f.end)
    }

    @Test fun dailyAdvanceKeepsBillsBack() {
        val rent = Bill(id = 9, name = "Rent", amount = 3_000_000, day = 10)
        val clues = listOf(clue(today.minusDays(1), 12, 2, 10_000), clue(today, 9, 3, 300))
        val t = Ledger.today(plan, clues, listOf(rent), now, zone)
        // 90 000 − rent 30 000 − yesterday 10 000 = 50 000 over 11 days (25 Oct to 5 Nov).
        assertEquals(11, t.daysLeft)
        assertEquals(5_000_000, t.left)
        assertEquals(5_000_000 / 11, t.perDay)
        assertEquals(5_000_000 / 11 - 30_000, t.leftToday)
        val paid = Ledger.today(plan, clues + Clue(id = 99, amount = 3_000_000, at = now, billId = 9), listOf(rent.copy(paidPeriod = t.period.id)), now, zone)
        assertEquals(t.perDay, paid.perDay)
        assertEquals(t.leftToday, paid.leftToday)
    }

    // ---- the detective

    @Test fun nightWorkOpensACase() {
        val clues = (1..4).map { clue(today.minusDays(it * 2L), 23, 1, 900) } + clue(today.minusDays(3), 13, 1, 900)
        val cases = Detective.open(state(clues), plan, now, zone)
        assertEquals(1, cases.size)
        assertEquals(Pattern.NIGHT, cases[0].pattern); assertEquals(4, cases[0].count)
    }

    @Test fun citizensAreNeverOnACase() {
        val clues = (1..10).map { clue(today.minusDays(it.toLong()), 23, 2, 300) }
        assertTrue(Detective.open(state(clues), plan, now, zone).isEmpty())
    }

    @Test fun smallChangeAddsUp() {
        val clues = (0..8).map { clue(today.minusDays(it.toLong()), 9, 3, 250) }
        val c = Detective.open(state(clues), plan, now, zone).single()
        assertEquals(Pattern.DRIP, c.pattern)
    }

    @Test fun aTailCrossed() {
        val clues = listOf(clue(today.minusDays(3), 13, 2, 20_000), clue(today.minusDays(1), 13, 2, 15_000))
        val c = Detective.open(state(clues, sus = listOf(grocer.copy(limit = 3_000_000))), plan, now, zone).single()
        assertEquals(Pattern.LIMIT, c.pattern); assertEquals(116, c.stat)
    }

    @Test fun oneCasePerSuspectAndAWeekOfPeace() {
        val clues = (1..4).map { clue(today.minusDays(it * 2L), 23, 1, 900) }
        val open = Case(id = 1, number = 1, pattern = Pattern.DRIP, suspectId = 1, fromAt = 0, toAt = 0, openedAt = now - DAY, sum = 0, count = 0)
        assertTrue(Detective.open(state(clues, listOf(open)), plan, now, zone).isEmpty())
        val closed = open.copy(verdict = Verdict.ACQUITTED, closedAt = now - 2 * DAY)
        assertTrue(Detective.open(state(clues, listOf(closed)), plan, now, zone).isEmpty())
        val old = closed.copy(openedAt = now - 30 * DAY, closedAt = now - 20 * DAY)
        assertEquals(2, Detective.open(state(clues, listOf(old)), plan, now, zone).single().number)
    }

    @Test fun ghostsComeMonthly() {
        val clues = listOf(
            clue(LocalDate.of(2026, 7, 12), 3, null, 399, "Кинопоиск подписка"),
            clue(LocalDate.of(2026, 8, 12), 3, null, 399, "Кинопоиск подписка"),
            clue(LocalDate.of(2026, 9, 12), 3, null, 399, "КИНОПОИСК подписка"),
            clue(LocalDate.of(2026, 9, 3), 9, 3, 200, "Кофемания"),
            clue(LocalDate.of(2026, 9, 5), 9, 3, 200, "Кофемания"),
        )
        val shop = (0..8).map { clue(LocalDate.of(2026, 7, 4).plusDays(it * 10L), 18, 2, if (it % 3 == 0) 1500 else 900 + it * 97, "Trader Joe's") }
        val g = Ghosts.find(clues + shop, emptyList(), now, zone).single()
        assertEquals(12, g.day); assertEquals(39_900, g.amount); assertEquals(3, g.count)
    }

    // ---- the report

    private val lines by lazy { File("src/main/assets/lines.tsv").inputStream().use { Lines.read(it) } }

    @Test fun everyConditionHasLines() {
        for (c in Cond.entries) assertTrue("no lines for $c", lines.of(c).size >= 3)
        for (l in lines.items) {
            assertEquals(l.ru, l.ru.count { it == '{' }, l.ru.count { it == '}' })
            for (s in listOf(l.ru, l.en)) assertTrue(s, Regex("\\{(\\w+)\\}").findAll(s).all { it.groupValues[1] in setOf("who", "n", "sum", "day") })
            assertTrue(l.en, !l.en.contains('|'))
        }
        val w = Words(true, Gender.SHE)
        assertEquals("устала", w.g("устал{|а}"))
        assertEquals("научилась", w.g("научил{ся|ась}"))
    }

    @Test fun quietDay() {
        val f = Report.facts(state(listOf(clue(today.minusDays(1), 12, 3, 300))), plan, today, zone)
        assertEquals(Cond.QUIET, f.cond)
        assertNotNull(Report.pick(lines, f))
        val night = Report.facts(state(listOf(clue(today, 23, 1, 300))), plan, today, zone)
        assertEquals(Cond.LATE, night.cond)
    }

    @Test fun money() {
        val ru = Words(true, null)
        assertEquals("1 840,50 ₽", ru.money(184_050))
        assertEquals("−612 ₽", ru.money(-61_200))
        val en = Words(false, null, "$", true)
        assertEquals("$1,840", en.money(184_000))
    }
}
