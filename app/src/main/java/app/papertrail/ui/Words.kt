package app.papertrail.ui

import android.content.Context
import app.papertrail.data.Settings
import app.papertrail.domain.Case
import app.papertrail.domain.Cast
import app.papertrail.domain.DayFacts
import app.papertrail.domain.Gender
import app.papertrail.domain.GhostState
import app.papertrail.domain.Issue
import app.papertrail.domain.Lead
import app.papertrail.domain.Line
import app.papertrail.domain.Measure
import app.papertrail.domain.Motive
import app.papertrail.domain.Pattern
import app.papertrail.domain.Role
import app.papertrail.domain.State
import app.papertrail.domain.Suspect
import app.papertrail.domain.Verdict
import app.papertrail.ru
import java.time.LocalDate
import kotlin.math.abs

/** The office's words that come from data rather than resources, in both languages. */
class Words(val ru: Boolean, val gender: Gender?, val currency: String = "₽", val currencyFirst: Boolean = false) {
    fun t(r: String, e: String) = if (ru) g(r) else e

    /** Picks the form in Russian {he|she} marks; without a known gender both show, as «устал(а)». */
    fun g(s: String): String = MARK.replace(s) { m ->
        val (he, she) = m.destructured
        when (gender) {
            Gender.HE -> he
            Gender.SHE -> she
            null -> if (she.startsWith(he)) "$he(${she.removePrefix(he)})" else "$he/$she"
        }
    }

    fun plural(n: Long, one: String, few: String, many: String): String {
        val a = abs(n) % 100; val b = a % 10
        return when { a in 11..19 -> many; b == 1L -> one; b in 2..4 -> few; else -> many }
    }

    fun n(n: Int, one: String, few: String, many: String, en1: String, enN: String) = if (ru) "$n ${plural(n.toLong(), one, few, many)}" else "$n ${if (n == 1) en1 else enN}"

    // ---- money

    /** 184050 → «1 840,50 ₽» or «$1,840.50»; whole sums without the kopecks. */
    fun money(minor: Long, sign: Boolean = false): String {
        val neg = minor < 0
        val a = abs(minor)
        val whole = a / 100
        val frac = a % 100
        val sep = if (ru) ' ' else ','
        val w = whole.toString().reversed().chunked(3).joinToString(sep.toString()).reversed()
        val num = if (frac == 0L) w else w + (if (ru) "," else ".") + frac.toString().padStart(2, '0')
        val body = if (currencyFirst) "$currency$num" else "$num $currency"
        return (if (neg) "−" else if (sign) "+" else "") + body
    }

    /** Just the number, for the matchbook's big figures. */
    fun amount(minor: Long): String = money(minor).replace(currency, "").trim(' ', ' ')

    // ---- suspects

    fun name(s: Suspect?): String = when {
        s == null -> t("Не разобрано", "Unsorted")
        s.name.isNotBlank() -> s.name
        else -> Cast[s.preset]?.let { if (ru) it.ru else it.en } ?: "?"
    }

    fun alias(s: Suspect?): String = when {
        s == null -> t("Неизвестный", "Person unknown")
        s.alias.isNotBlank() -> s.alias
        else -> Cast[s.preset]?.let { if (ru) it.aliasRu else it.aliasEn } ?: name(s)
    }

    fun mo(s: Suspect): String = Cast[s.preset]?.let { if (ru) it.moRu else it.moEn }
        ?: if (s.role == Role.CITIZEN) t("честный гражданин", "an honest citizen") else t("почерк пока не ясен", "no known pattern yet")

    fun role(r: Role) = when (r) { Role.SUSPECT -> t("Подозреваемый", "Suspect"); Role.CITIZEN -> t("Честный гражданин", "Honest citizen") }

    // ---- cases

    fun caseTitle(c: Case) = t("Дело №${c.number}", "Case no. ${c.number}")

    fun pattern(p: Pattern) = when (p) {
        Pattern.LIMIT -> t("Нарушение наружки", "Tail broken")
        Pattern.SPIKE -> t("Внезапный размах", "Sudden spree")
        Pattern.NIGHT -> t("Ночные вылазки", "Night work")
        Pattern.DRIP -> t("Тысяча мелочей", "Death by small change")
        Pattern.WEEKDAY -> t("Один и тот же день", "Same day every week")
    }

    /** What the case is about, in one typed sentence. */
    fun caseLead(c: Case, s: State): String {
        val who = alias(s.suspect(c.suspectId))
        return when (c.pattern) {
            Pattern.LIMIT -> t("$who перешёл черту: ${money(c.sum)} за период, это ${c.stat}% от наружки.", "$who crossed the line: ${money(c.sum)} this period, ${c.stat}% of the tail.")
                .let { if (ru && s.suspect(c.suspectId)?.preset == "operator") it.replace("перешёл", "перешла") else it }
            Pattern.SPIKE -> t("$who: ${money(c.sum)} за неделю — на ${c.stat}% больше, чем обычно.", "$who: ${money(c.sum)} in a week, ${c.stat}% more than usual.")
            Pattern.NIGHT -> t("$who: ${n(c.count, "улика", "улики", "улик", "clue", "clues")} после десяти вечера за две недели. Всего ${money(c.sum)}.", "$who: ${n(c.count, "", "", "", "clue", "clues")} after ten at night in two weeks. ${money(c.sum)} in all.")
            Pattern.DRIP -> t("$who: ${n(c.count, "мелкая улика", "мелкие улики", "мелких улик", "", "")} за две недели. По отдельности — ерунда, вместе — ${money(c.sum)}.", "$who: ${c.count} small clues in two weeks. Nothing on their own; together, ${money(c.sum)}.")
            Pattern.WEEKDAY -> t("$who приходит ${weekdayOn(c.stat)}: ${n(c.count, "улика", "улики", "улик", "", "")} за четыре недели, ${money(c.sum)}.", "$who comes ${weekdayOn(c.stat)}: ${c.count} clues in four weeks, ${money(c.sum)}.")
        }
    }

    /** The detective's hunch, under the threads on the board. */
    fun hunch(c: Case) = when (c.pattern) {
        Pattern.LIMIT -> t("Черта была проведена не зря. Вопрос в том, почему её перешли.", "The line was drawn for a reason. The question is why it was crossed.")
        Pattern.SPIKE -> t("Что-то случилось на этой неделе. Праздник? Усталость? Распродажа?", "Something happened this week. A celebration? Tiredness? A sale?")
        Pattern.NIGHT -> t("Все нити ведут в позднее время. Днём я так не делаю.", "Every thread leads to late hours. I don't do this in daylight.")
        Pattern.DRIP -> t("Ни одна улика не выглядит опасной. В этом и весь трюк.", "Not one clue looks dangerous. That's the whole trick.")
        Pattern.WEEKDAY -> t("Один и тот же день недели. Значит, есть привычка — или повод.", "The same day of the week. So there's a habit, or a reason.")
    }

    fun motive(m: Motive) = when (m) {
        Motive.TIRED -> t("усталость", "tiredness")
        Motive.BORED -> t("скука", "boredom")
        Motive.TREAT -> t("хотел{|а} порадовать себя", "a treat for myself")
        Motive.SALE -> t("скидка, реклама", "a sale, an ad")
        Motive.COMPANY -> t("за компанию", "for company")
        Motive.NEEDED -> t("так было нужно", "it was needed")
    }

    fun verdict(v: Verdict) = when (v) {
        Verdict.OPEN -> t("в работе", "open")
        Verdict.ACQUITTED -> t("оправдан", "acquitted")
        Verdict.GUILTY -> t("виновен", "guilty")
    }

    fun measure(m: Measure) = when (m) {
        Measure.NONE -> ""
        Measure.WATCH -> t("под наблюдением", "under watch")
        Measure.LIMIT -> t("наружка", "a tail")
        Measure.BAN -> t("запрет на неделю", "banned for a week")
    }

    fun measureSub(m: Measure) = when (m) {
        Measure.NONE -> ""
        Measure.WATCH -> t("просто присматривать", "just keep an eye")
        Measure.LIMIT -> t("лимит на месяц", "a monthly limit")
        Measure.BAN -> t("семь дней не подпускать", "keep away seven days")
    }

    fun ghost(st: GhostState) = when (st) {
        GhostState.SEEN -> t("является", "haunting")
        GhostState.EXORCISED -> t("изгнан", "exorcised")
        GhostState.ALLOWED -> t("пусть живёт", "allowed to stay")
    }

    // ---- ranks

    fun rank(i: Int) = when (i) {
        0 -> t("Стажёр", "Rookie")
        1 -> t("Младший сыщик", "Junior detective")
        2 -> t("Сыщик", "Detective")
        3 -> t("Старший сыщик", "Senior detective")
        4 -> t("Инспектор", "Inspector")
        else -> t("Легенда отдела", "Legend of the bureau")
    }

    // ---- the evening report

    fun line(l: Line, f: DayFacts, s: State): String {
        val raw = if (ru) l.ru else l.en
        val sum = if (f.cond == app.papertrail.domain.Cond.FEE) f.fee else f.sum
        return g(raw).replace("{who}", s.suspect(f.who)?.let { alias(it) } ?: t("город", "the town"))
            .replace("{n}", f.count.toString()).replace("{sum}", money(sum))
            .replace("{day}", weekday(f.date).replaceFirstChar { it.uppercase() })
    }

    // ---- the paper

    fun paperName() = t("Городской вестник", "The City Ledger")

    fun headline(i: Issue, s: State): Pair<String, String> {
        val who = s.suspect(i.star)?.let { alias(it) }.orEmpty()
        return when (i.lead) {
            Lead.CAUGHT -> t("$who пойман!", "$who caught!").uppercase() to
                t("Траты по делу «${name(s.suspect(i.star))}» упали на ${-i.change}%. Город вздохнул спокойно.", "Spending on «${name(s.suspect(i.star))}» fell ${-i.change}%. The town breathes easier.")
            Lead.AT_LARGE -> t("$who разгуливает по городу", "$who on the loose").uppercase() to
                t("Траты по делу «${name(s.suspect(i.star))}» выросли на ${i.change}%. Сыщик идёт по следу.", "Spending on «${name(s.suspect(i.star))}» up ${i.change}%. The detective is on the trail.")
            Lead.SAVED -> t("Касса цела!", "The till holds!").uppercase() to
                t("Аванс выдержал: осталось ${money(i.advance - i.spent)}. Клиент доволен.", "The advance held: ${money(i.advance - i.spent)} left. The client is pleased.")
            Lead.OVERSPENT -> t("Деньги утекли", "Money down the drain").uppercase() to
                t("Перерасход ${money(i.spent - i.advance)}. Следствие продолжается.", "Over by ${money(i.spent - i.advance)}. The investigation goes on.")
            Lead.QUIET_MONTH -> t("В городе спокойно", "All quiet in town").uppercase() to
                t("Без громких дел: ${money(i.spent)} за ${n(i.days, "день", "дня", "дней", "day", "days")}.", "No big cases: ${money(i.spent)} over ${n(i.days, "", "", "", "day", "days")}.")
            Lead.FIRST_ISSUE -> t("Сыщик открыл контору", "Detective opens office").uppercase() to
                t("Первый выпуск. Улики собираются, картотека растёт.", "First issue. Clues are coming in, the files are growing.")
        }
    }

    // ---- dates

    fun date(d: LocalDate) = if (ru) "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}" else "${MONTHS_EN[d.monthValue - 1]} ${d.dayOfMonth}"
    fun shortDate(d: LocalDate) = if (ru) "${d.dayOfMonth}.${d.monthValue.toString().padStart(2, '0')}" else "${d.monthValue}/${d.dayOfMonth}"
    fun month(d: LocalDate) = if (ru) MONTHS[d.monthValue - 1] else MONTHS_EN[d.monthValue - 1]
    fun weekday(d: LocalDate) = if (ru) WEEKDAYS[d.dayOfWeek.value - 1] else WEEKDAYS_EN[d.dayOfWeek.value - 1]
    fun weekdayShort(dow: Int) = if (ru) listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")[dow - 1] else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[dow - 1]
    /** «по пятницам» / «on Fridays». */
    fun weekdayOn(dow: Int) = if (ru) listOf("по понедельникам", "по вторникам", "по средам", "по четвергам", "по пятницам", "по субботам", "по воскресеньям")[dow - 1]
        else "on ${WEEKDAYS_EN[dow - 1]}s"
    fun time(at: Long, zone: java.time.ZoneId): String = java.time.Instant.ofEpochMilli(at).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    companion object {
        private val MARK = Regex("\\{([^|}]*)\\|([^}]*)\\}")
        val MONTHS = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
        val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val WEEKDAYS = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        val WEEKDAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun of(ctx: Context, s: Settings) = Words(ctx.ru, s.gender, s.currency, s.currencyFirst)
    }
}
