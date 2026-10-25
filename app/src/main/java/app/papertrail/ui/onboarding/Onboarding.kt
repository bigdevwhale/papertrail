package app.papertrail.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.data.Settings
import app.papertrail.domain.Cast
import app.papertrail.domain.Gender
import app.papertrail.domain.Role
import app.papertrail.domain.Suspect
import app.papertrail.sound.Snd
import app.papertrail.ui.Words
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Chip
import app.papertrail.ui.components.Flow
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Note
import app.papertrail.ui.components.Portrait
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.settings.Advance
import app.papertrail.ui.settings.CURRENCIES
import app.papertrail.ui.settings.Informants
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

private enum class Page { INTRO, WHO, ADVANCE, CAST, INFORMANT }

/** The first visit: the case is taken, the advance named, the suspects hired, the informant briefed. */
@Composable
fun Onboarding(w: Words, st: Settings) {
    val c = LocalContext.current.container
    val pages = Page.entries.filter { it != Page.WHO || w.ru }
    var i by remember { mutableIntStateOf(0) }
    var cast by remember { mutableStateOf(Cast.STARTING.toSet()) }
    val page = pages[i]
    BackHandler(enabled = i > 0) { i-- }
    fun next() {
        c.sfx.play(Snd.KEY)
        if (i < pages.lastIndex) i++ else {
            val keys = Cast.ALL.map { it.key }.filter { it in cast }
            c.write {
                repo.hire(keys)
                settings.update { it.copy(onboarded = true) }
            }
        }
    }
    fun set(f: (Settings) -> Settings) = c.write { settings.update(f) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            when (page) {
                Page.INTRO -> Intro(w)
                Page.WHO -> {
                    Text("Кто ведёт дело?", style = T.Title)
                    Gap(8)
                    Text("Рапорты пишутся от первого лица: «я устал» или «я устала».", style = T.Body.copy(color = C.fog))
                    Gap(16)
                    Flow {
                        Chip("Сыщик", st.gender == Gender.HE) { set { it.copy(gender = Gender.HE) } }
                        Chip("Сыщица", st.gender == Gender.SHE) { set { it.copy(gender = Gender.SHE) } }
                    }
                }
                Page.ADVANCE -> {
                    Text(w.t("Аванс от клиента", "The client's advance"), style = T.Title)
                    Gap(8)
                    Text(
                        w.t("Клиент — это вы. Каждый месяц вы выдаёте себе аванс на жизнь, и контора следит, чтобы он дожил до следующего гонорара. Из аванса считается сумма на каждый день — она будет на спичечном коробке.",
                            "The client is you. Each month you give yourself an advance for living, and the office makes sure it lasts till the next fee. From the advance comes a sum for each day; it'll be on the matchbook."),
                        style = T.Body.copy(color = C.fog),
                    )
                    Label(w.t("Валюта", "Currency"))
                    Flow { for ((sym, first) in CURRENCIES) Chip(sym, st.currency == sym) { set { it.copy(currency = sym, currencyFirst = first) } } }
                    Advance(w, st) { a, d -> set { it.copy(advance = a, payday = d) } }
                    if (st.advance > 0) Text(w.t("Записано: ${w.money(st.advance)}, с ${st.payday}-го числа.", "Noted: ${w.money(st.advance)}, from the ${st.payday}th."), style = T.Small.copy(color = C.paper), modifier = Modifier.padding(top = 8.dp))
                }
                Page.CAST -> {
                    Text(w.t("Картотека", "The files"), style = T.Title)
                    Gap(8)
                    Text(
                        w.t("Каждая категория трат — лицо в картотеке. Подозреваемых можно привлечь к делу, честных граждан — нет, они нужны городу. Отметьте, кого завести; своих можно добавить потом.",
                            "Each kind of spending is a face in the files. Suspects can be put on a case; honest citizens can't, the town needs them. Mark who to file; you can add your own later."),
                        style = T.Body.copy(color = C.fog),
                    )
                    for (role in Role.entries) {
                        Label(if (role == Role.SUSPECT) w.t("Подозреваемые", "Suspects") else w.t("Честные граждане", "Honest citizens"))
                        for (row in Cast.ALL.filter { it.role == role }.chunked(3)) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (ch in row) {
                                val on = ch.key in cast
                                Column(Modifier.weight(1f).clickable { cast = if (on) cast - ch.key else cast + ch.key }) {
                                    Box {
                                        Portrait(Suspect(preset = ch.key), Modifier.fillMaxWidth().alpha(if (on) 1f else .35f))
                                        if (on) Stamp(w.t("В деле", "Filed"), Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), angle = -8f)
                                    }
                                    Text(if (w.ru) ch.aliasRu else ch.aliasEn, style = T.TypeS.copy(color = if (on) C.paper else C.smoke), maxLines = 1)
                                    Text(if (w.ru) ch.ru else ch.en, style = T.Small, maxLines = 2)
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                Page.INFORMANT -> {
                    Text(w.t("Осведомитель", "The informant"), style = T.Title)
                    Gap(8)
                    Text(
                        w.t("Улики можно вносить на машинке. А можно завести осведомителя: он слушает уведомления банка и кладёт конверт на стол сам. Если подозреваемый уже дважды бывал в этом месте, конверт разберётся без вас.",
                            "Clues can be typed in. Or you can take on an informant: it listens to the bank's notifications and puts the envelope on the desk itself. If a suspect has been to that place twice before, the envelope sorts itself."),
                        style = T.Body.copy(color = C.fog),
                    )
                    Informants(w, st) { set(it) }
                    Text(w.t("Это можно сделать и позже, в настройках.", "This can wait for settings, too."), style = T.Small, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (i > 0) Btn(w.t("Назад", "Back"), Modifier.weight(1f), look = Look.GHOST) { i-- }
            val ok = when (page) {
                Page.ADVANCE -> st.advance > 0
                Page.CAST -> cast.isNotEmpty()
                else -> true
            }
            Btn(
                when (page) { Page.INTRO -> w.t("Взяться за дело", "Take the case"); Page.INFORMANT -> w.t("Открыть контору", "Open the office"); else -> w.t("Дальше", "Next") },
                Modifier.weight(if (i > 0) 1.6f else 1f), enabled = ok,
            ) { next() }
        }
    }
}

@Composable
private fun Intro(w: Words) {
    Gap(20)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Portrait(null, Modifier.width(150.dp).rotate(-2f))
        Stamp(w.t("Висяк", "Cold case"), Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 10.dp), angle = -12f)
    }
    Gap(24)
    Text(w.t("ВИСЯК", "PAPER TRAIL"), style = T.Title.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    Gap(16)
    Note(Modifier.rotate(-.8f), color = C.paperHi) {
        Text(
            w.t("Дождь шёл третью неделю. Деньги пропадали — не все сразу, а понемногу, как вода из худого ведра. Клиент пришёл{|а} ко мне без шляпы и сказал{|а} одно: «Найдите, куда».\n\nКлиентом был{|а} я сам{|а}.",
                "It had been raining for three weeks. The money kept vanishing, not all at once but bit by bit, like water from a leaky bucket. The client came in without a hat and said only: «Find out where it goes.»\n\nThe client was me."),
            style = T.Type,
        )
    }
    Gap(14)
    Text(
        w.t("Траты — улики. Категории — подозреваемые. Когда в уликах проступает почерк, открывается дело: вы ищете мотив и выносите вердикт. Никакого стыда — только следствие.",
            "Spending is evidence. Categories are suspects. When the clues show a pattern, a case opens: you find the motive and hand down the verdict. No shame, just detective work."),
        style = T.Body.copy(color = C.fog),
    )
}
