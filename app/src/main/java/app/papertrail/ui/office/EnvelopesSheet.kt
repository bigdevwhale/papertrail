package app.papertrail.ui.office

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.papertrail.container
import app.papertrail.domain.Source
import app.papertrail.domain.date
import app.papertrail.sound.Snd
import app.papertrail.ui.Home
import app.papertrail.ui.components.Btn
import app.papertrail.ui.components.Gap
import app.papertrail.ui.components.Label
import app.papertrail.ui.components.Look
import app.papertrail.ui.components.Note
import app.papertrail.ui.components.Sheet
import app.papertrail.ui.components.Stamp
import app.papertrail.ui.components.SuspectPicker
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

/** Unsorted envelopes, one at a time: a tap on a face files it, and the next one slides in. */
@Composable
fun EnvelopesSheet(h: Home, onClose: () -> Unit) {
    val w = h.w
    val c = LocalContext.current.container
    val env = h.s.envelopes
    val e = env.firstOrNull()
    LaunchedEffect(e == null) { if (e == null) onClose() }
    if (e == null) return
    LaunchedEffect(e.id) { c.sfx.play(Snd.ENVELOPE, .7f) }
    val suggested = c.repo.suggest(h.s, e.place)
    Sheet(w.t("Конверты · ${env.size}", "Envelopes · ${env.size}"), onClose, w.t("Позже", "Later")) {
        Gap(6)
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Note(Modifier.rotate(1.2f), color = C.paperHi) {
                Text(w.date(e.at.date(h.zone)) + " · " + w.time(e.at, h.zone), style = T.TypeS)
                Text(w.money(e.amount), style = T.Big, modifier = Modifier.padding(vertical = 6.dp))
                Text(e.place.ifBlank { w.t("место не указано", "no place given") }, style = T.Type)
                if (e.source == Source.NOTICE) Text(w.t("от осведомителя", "from the informant"), style = T.TypeS, modifier = Modifier.padding(top = 4.dp))
            }
            Stamp(w.t("Не разобрано", "Unsorted"), Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp), angle = 9f)
        }
        Label(w.t("Кто это сделал?", "Who did it?"))
        SuspectPicker(w, h.s.files, null, suggested?.id) { sus ->
            c.sfx.play(Snd.PIN)
            c.write { repo.sort(e.id, sus.id) }
        }
        if (suggested != null) Text(
            w.t("Похоже на почерк: ${w.alias(suggested)}", "Looks like the work of: ${w.alias(suggested)}"),
            style = T.Small, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Gap(18)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(w.t("Это не трата", "Not a spend"), Modifier.weight(1f), look = Look.GHOST) { c.write { repo.delete(e) } }
            if (suggested != null) Btn(w.t("Да, ${w.alias(suggested)}", "Yes, ${w.alias(suggested)}"), Modifier.weight(1f)) {
                c.sfx.play(Snd.PIN)
                c.write { repo.sort(e.id, suggested.id) }
            }
        }
        Gap(12)
    }
}
