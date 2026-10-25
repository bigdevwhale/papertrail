package app.papertrail.domain

/**
 * The house characters: each a category of spending with a face, a nickname and a way of working.
 * [hat] and [mark] pick the portrait's pieces in ui/art/Mugshot.kt.
 */
data class Character(
    val key: String,
    val role: Role,
    val ru: String, val en: String,
    val aliasRu: String, val aliasEn: String,
    /** Their way of working, as the file card puts it. */
    val moRu: String, val moEn: String,
    val hat: Hat, val mark: Mark,
    /** Words in a bank's place name that point at this one. */
    val hints: List<String> = emptyList(),
)

enum class Hat { NONE, CAP, FLAT, BOWLER, FEDORA, TOP, VISOR, CHEF, NURSE, CONDUCTOR }
enum class Mark { NONE, BOX, CUP, TIE, BOW, GLASSES, MOUSTACHE, MIRROR, HEADSET, SCAR, CIGAR, PEARLS, MONOCLE }

object Cast {
    val ALL = listOf(
        Character("courier", Role.SUSPECT, "Доставка еды", "Food delivery", "Ночной Курьер", "The Night Courier",
            "приходит после десяти, когда нет сил готовить", "comes after ten, when there's no strength left to cook",
            Hat.CAP, Mark.BOX, listOf("delivery", "доставк", "яндекс еда", "eda", "deliveroo", "uber eats", "doordash", "самокат", "wolt", "glovo", "кухня на районе")),
        Character("boxman", Role.SUSPECT, "Маркетплейсы", "Online shops", "Коробочник", "The Box Man",
            "оживляется в дни распродаж и по ночам", "comes alive on sale days and at night",
            Hat.FEDORA, Mark.GLASSES, listOf("ozon", "wildberries", "wb", "amazon", "aliexpress", "ali", "temu", "ebay", "market", "маркет", "мегамаркет", "lamoda")),
        Character("barista", Role.SUSPECT, "Кофе и перекусы", "Coffee and snacks", "Бариста", "The Barista",
            "берёт по мелочи, зато каждый день", "takes a little, but every day",
            Hat.NONE, Mark.MOUSTACHE, listOf("coffee", "кофе", "cafe", "кафе", "starbucks", "costa", "cofix", "шоколадница", "bakery", "пекарн")),
        Character("maitre", Role.SUSPECT, "Рестораны и бары", "Restaurants and bars", "Метрдотель", "The Maître d’",
            "любит пятницы и большие компании", "fond of Fridays and big tables",
            Hat.NONE, Mark.BOW, listOf("restaurant", "ресторан", "bar", "бар", "pub", "паб", "grill", "гриль", "trattoria", "bistro")),
        Character("cabbie", Role.SUSPECT, "Такси", "Taxis", "Водила", "The Cabbie",
            "работает в дождь и когда ты опаздываешь", "works the rain and the mornings you're late",
            Hat.FLAT, Mark.CIGAR, listOf("taxi", "такси", "uber", "yandex go", "bolt", "lyft", "ситимобил", "яндекс go")),
        Character("dandy", Role.SUSPECT, "Одежда и красота", "Clothes and looks", "Франт", "The Dandy",
            "заглядывает, когда хочется стать кем-то другим", "drops by when you'd like to be someone else",
            Hat.BOWLER, Mark.TIE, listOf("zara", "h&m", "uniqlo", "sela", "gloria", "befree", "gold apple", "золотое яблоко", "летуаль", "sephora", "барбер", "салон")),
        Character("croupier", Role.SUSPECT, "Развлечения и игры", "Fun and games", "Крупье", "The Croupier",
            "сдаёт карты по выходным", "deals on weekends",
            Hat.VISOR, Mark.SCAR, listOf("steam", "playstation", "xbox", "nintendo", "кино", "cinema", "билет", "ticket", "concert", "концерт", "games")),
        Character("grocer", Role.CITIZEN, "Продукты", "Groceries", "Бакалейщик", "The Grocer",
            "честный гражданин: без него никуда", "an honest citizen: you can't do without him",
            Hat.CHEF, Mark.NONE, listOf("пятерочка", "пятёрочка", "магнит", "перекресток", "перекрёсток", "лента", "ашан", "вкусвилл", "дикси", "spar", "tesco", "lidl", "aldi", "walmart", "grocery", "supermarket", "продукт")),
        Character("landlord", Role.CITIZEN, "Жильё и коммуналка", "Rent and bills", "Домовладелец", "The Landlord",
            "приходит раз в месяц, всегда вовремя", "comes once a month, never late",
            Hat.TOP, Mark.MONOCLE, listOf("жкх", "жку", "квартплат", "rent", "utility", "мосэнерго", "энергосбыт", "газ", "water", "electric")),
        Character("doc", Role.CITIZEN, "Здоровье", "Health", "Доктор", "The Doc",
            "без него в этом городе не выжить", "you won't last in this town without him",
            Hat.NURSE, Mark.MIRROR, listOf("аптека", "apteka", "pharmacy", "клиника", "clinic", "стоматолог", "dental", "медицин", "invitro", "гемотест")),
        Character("conductor", Role.CITIZEN, "Транспорт", "Transport", "Кондуктор", "The Conductor",
            "пробивает билеты по утрам", "punches tickets in the morning",
            Hat.CONDUCTOR, Mark.NONE, listOf("метро", "metro", "тройка", "troika", "транспорт", "transport", "ржд", "rail", "bus", "автобус", "азс", "fuel", "лукойл", "газпромнефть", "shell")),
        Character("operator", Role.CITIZEN, "Связь и интернет", "Phone and internet", "Телефонистка", "The Operator",
            "соединяет с миром за небольшую плату", "connects you to the world, for a small fee",
            Hat.NONE, Mark.HEADSET, listOf("мтс", "mts", "билайн", "beeline", "мегафон", "megafon", "tele2", "теле2", "ростелеком", "vodafone", "internet", "интернет")),
    )

    operator fun get(key: String?) = ALL.firstOrNull { it.key == key }

    /** The ones put in the files on the first day unless the detective says otherwise. */
    val STARTING = listOf("courier", "boxman", "barista", "maitre", "cabbie", "dandy", "grocer", "landlord", "conductor")

    /** Who a place most likely belongs to, among the suspects in the files. */
    fun guess(place: String, files: List<Suspect>): Suspect? {
        val p = place.lowercase().replace('ё', 'е')
        if (p.isBlank()) return null
        return files.firstOrNull { s -> Cast[s.preset]?.hints?.any { p.contains(it.replace('ё', 'е')) } == true }
    }
}
