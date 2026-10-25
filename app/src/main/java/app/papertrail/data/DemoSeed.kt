package app.papertrail.data

import app.papertrail.AppContainer
import app.papertrail.domain.Bill
import app.papertrail.domain.Cast
import app.papertrail.domain.Clue
import app.papertrail.domain.DAY
import app.papertrail.domain.Gender
import app.papertrail.domain.GhostState
import app.papertrail.domain.Kind
import app.papertrail.domain.Measure
import app.papertrail.domain.Motive
import app.papertrail.domain.Source
import app.papertrail.domain.Ticket
import app.papertrail.domain.Verdict
import app.papertrail.domain.start
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/**
 * Three months of a detective's life for the demo build and the screen renders: daily coffee, a courier
 * who comes at night lately, Friday dinners, a sale-day spree, subscriptions, bills, cash, a ticket to the sea.
 * The clock is wound day by day so cases open and close the way they would have.
 */
object DemoSeed {
    /** Days ago the Night Courier came after ten. */
    private val LATE_DINNERS = setOf(1, 3, 4, 7, 9, 12)

    suspend fun seedIfEmpty(c: AppContainer) {
        if (c.repo.refresh().suspects.isNotEmpty()) return
        val ru = Locale.getDefault().language == "ru"
        val zone = ZoneId.systemDefault()
        val r = Random(1946)
        val plan = Settings(advance = if (ru) 12_000_000 else 400_000, payday = 5).plan
        val k = if (ru) 1L else 30L // Prices in roubles, or the same scale in dollars.
        c.settings.update {
            it.copy(onboarded = true, gender = Gender.HE, advance = plan.advance, payday = plan.payday, currency = if (ru) "₽" else "$", currencyFirst = !ru)
        }
        c.repo.hire(Cast.STARTING + listOf("croupier", "doc", "operator"))
        val s0 = c.repo.refresh()
        fun id(key: String) = s0.suspects.first { it.preset == key }.id

        val repo = c.repo
        val today = LocalDate.now(zone)
        val first = today.minusDays(96)
        val clues = mutableListOf<Clue>()
        fun at(d: LocalDate, h: Int, m: Int = r.nextInt(60)) = d.start(zone) + h * 3_600_000L + m * 60_000L
        fun rub(v: Int) = v * 100L / k
        fun add(d: LocalDate, h: Int, who: String?, amount: Long, place: String, src: Source = Source.NOTICE, kind: Kind = Kind.SPEND, cash: Boolean = false, solved: Boolean = false) {
            clues += Clue(kind = kind, amount = amount, at = at(d, h), suspectId = who?.let { id(it) }, place = place, source = src, cash = cash, solved = solved)
        }
        val grocers = if (ru) listOf("Пятёрочка", "ВкусВилл", "Перекрёсток") else listOf("Trader Joe's", "Whole Foods", "Safeway")
        val cafes = if (ru) listOf("Кофемания", "Cofix", "Пекарня у дома") else listOf("Blue Bottle", "Starbucks", "Corner Bakery")
        val couriers = if (ru) listOf("Яндекс Еда", "Самокат", "Кухня на районе") else listOf("Uber Eats", "DoorDash", "Deliveroo")
        val bars = if (ru) listOf("Ресторан «Пушкин»", "Бар «Стрелка»", "Гриль «Мясо»") else listOf("The Grill Room", "Harry's Bar", "Trattoria Roma")
        val shops = if (ru) listOf("Ozon", "Wildberries", "Мегамаркет") else listOf("Amazon", "eBay", "Etsy")

        var d = first
        while (!d.isAfter(today)) {
            val ago = (today.toEpochDay() - d.toEpochDay()).toInt()
            val dow = d.dayOfWeek.value
            if (d.dayOfMonth == plan.payday) add(d, 10, null, plan.advance * 13 / 10, if (ru) "Зарплата" else "Salary", kind = Kind.FEE)
            if (ago > 0 && r.nextFloat() < .55f) add(d, 18 + r.nextInt(3), "grocer", rub(400 + r.nextInt(1800)), grocers.random(r))
            // The Barista: small and nearly daily, more of it lately.
            if (r.nextFloat() < if (ago < 15) .8f else .5f) add(d, 8 + r.nextInt(3), "barista", rub(180 + r.nextInt(220)), cafes.random(r))
            // The Night Courier: now and then, then three weeks of late dinners.
            if (ago in LATE_DINNERS) add(d, 22 + ago % 2, "courier", rub(700 + r.nextInt(900)), couriers.random(r))
            else if (ago >= 14 && r.nextFloat() < .12f) add(d, 19 + r.nextInt(3), "courier", rub(700 + r.nextInt(900)), couriers.random(r))
            // The Maître d': Fridays.
            if (dow == 5 && r.nextFloat() < .85f) add(d, 20, "maitre", rub(2200 + r.nextInt(2600)), bars.random(r))
            if (r.nextFloat() < .18f) add(d, 9 + r.nextInt(10), "cabbie", rub(350 + r.nextInt(600)), if (ru) "Яндекс Go" else "Uber")
            if (r.nextFloat() < .5f && dow <= 5) add(d, 8, "conductor", rub(62), if (ru) "Метро, Тройка" else "Metro")
            // The Box Man: a sale-day spree eight days ago.
            if (ago in 6..8) add(d, 23, "boxman", rub(2500 + r.nextInt(3500)), shops.random(r))
            else if (r.nextFloat() < .1f) add(d, 13 + r.nextInt(8), "boxman", rub(600 + r.nextInt(2000)), shops.random(r))
            if (r.nextFloat() < .05f) add(d, 15, "dandy", rub(3000 + r.nextInt(5000)), if (ru) "Zara" else "Uniqlo")
            if (dow == 6 && r.nextFloat() < .35f) add(d, 19, "croupier", rub(600 + r.nextInt(900)), if (ru) "Кино «Октябрь»" else "Odeon Cinema")
            if (r.nextFloat() < .04f) add(d, 12, "doc", rub(900 + r.nextInt(2500)), if (ru) "Аптека Ригла" else "CVS Pharmacy")
            // Ghosts: the same day each month.
            if (d.dayOfMonth == 12) add(d, 3, null, rub(399), if (ru) "Кинопоиск подписка" else "Netflix subscription")
            if (d.dayOfMonth == 21) add(d, 4, null, rub(299), if (ru) "Яндекс Плюс" else "Spotify Premium")
            if (d.dayOfMonth == 27) add(d, 2, null, rub(749), if (ru) "Фитнес Клаб ежемесячно" else "Gym Club monthly")
            if (ago == 40) add(d, 14, null, rub(5000), if (ru) "Банкомат, выдача наличных" else "ATM withdrawal", cash = true, solved = true)
            if (ago == 11) add(d, 17, null, rub(3000), if (ru) "Банкомат, выдача наличных" else "ATM withdrawal", cash = true)
            if (ago == 23) add(d, 13, null, rub(2000), if (ru) "Снятие наличных" else "Cash withdrawal", cash = true)
            d = d.plusDays(1)
        }
        // Subscriptions belong to the Croupier, except the gym.
        val sorted = clues.map { cl ->
            if (cl.suspectId == null && cl.kind == Kind.SPEND && !cl.cash && !cl.place.contains("Фитнес") && !cl.place.contains("Gym")) cl.copy(suspectId = id("croupier")) else cl
        }.sortedBy { it.at }
        // Today's last two envelopes stay unsorted, for the desk.
        val unsorted = sorted.filter { it.at.let { t -> t > today.start(zone) - 2 * DAY } && it.kind == Kind.SPEND && it.suspectId != null && !it.cash }.takeLast(2).map { it.at }.toSet()
        val final = sorted.map { if (it.at in unsorted) it.copy(suspectId = null) else it }

        val rent = Bill(name = if (ru) "Аренда" else "Rent", amount = rub(32000), day = 10, suspectId = id("landlord"))
        val phone = Bill(name = if (ru) "Связь и интернет" else "Phone and internet", amount = rub(900), day = 28, suspectId = id("operator"))
        repo.raw { dao -> dao.insert(rent); dao.insert(phone) }

        // Replay the days so cases open as they would have, and the older ones get closed.
        val verdicts = listOf(Verdict.GUILTY to Motive.TIRED, Verdict.ACQUITTED to Motive.NEEDED, Verdict.GUILTY to Motive.SALE, Verdict.ACQUITTED to Motive.TREAT, Verdict.GUILTY to Motive.BORED)
        var vi = 0
        d = first
        val real = repo.clock
        while (!d.isAfter(today)) {
            val dayEnd = d.start(zone) + DAY - 60_000
            val todays = final.filter { it.at in d.start(zone) until d.start(zone) + DAY }
            val noon = d.start(zone) + 12 * 3_600_000L
            repo.clock = { noon }
            // Rent and the phone are paid on their day through the bill.
            repo.raw { dao -> dao.insertClues(todays.filter { it.at < noon }) }
            if (d.dayOfMonth == rent.day) repo.state.value?.bills?.firstOrNull { it.name == rent.name }?.let { repo.pay(it.id, plan) }
            if (d.dayOfMonth == phone.day) repo.state.value?.bills?.firstOrNull { it.name == phone.name }?.let { repo.pay(it.id, plan) }
            repo.clock = { dayEnd }
            repo.raw { dao -> dao.insertClues(todays.filter { it.at >= noon }) }
            if (d.dayOfWeek.value == 7 || d == today) {
                repo.investigate(plan)
                val s = repo.refresh()
                for (case in s.openCases.filter { dayEnd - it.openedAt > 4 * DAY && today.toEpochDay() - d.toEpochDay() > 5 }) {
                    val (v, m) = verdicts[vi++ % verdicts.size]
                    repo.verdict(case.id, v, m, if (v == Verdict.GUILTY) listOf(Measure.WATCH, Measure.LIMIT, Measure.WATCH)[vi % 3] else Measure.NONE, (case.sum * 9 / 10 / 10000) * 10000)
                }
            }
            if (d.dayOfMonth == plan.payday - 1 && d != today) repo.safe(rub(8000), if (ru) "остаток периода" else "left over")
            d = d.plusDays(1)
        }
        repo.clock = real
        val s = repo.refresh()
        // One ghost exorcised long ago, the others still to decide.
        s.ghosts.firstOrNull { it.label.contains("Фитнес") || it.label.contains("Gym") }?.let { repo.ghost(it.id, GhostState.EXORCISED) }
        repo.saveTicket(Ticket(title = if (ru) "Море в июле" else "The sea in July", target = rub(120000), createdAt = 0))
        repo.saveTicket(Ticket(title = if (ru) "Новая печатная машинка" else "A new typewriter", target = rub(18000), createdAt = 0, style = 1))
        val t = repo.refresh().tickets
        repo.stamp(t[0].id, rub(14000))
        repo.stamp(t[1].id, rub(9000))
        repo.investigate(plan)
    }
}
