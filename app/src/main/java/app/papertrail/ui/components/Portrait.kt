package app.papertrail.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.papertrail.domain.Suspect
import app.papertrail.ui.Words
import app.papertrail.ui.art.Mugshot.mugshot
import app.papertrail.ui.art.Mugshot.unknown
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T

/** A suspect's police photo; «person unknown» without one. */
@Composable
fun Portrait(s: Suspect?, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(100f / 120f)) { if (s == null) unknown() else mugshot(s) }
}

/** The files as a row of small photos to pick from; the picked one framed in red. */
@Composable
fun SuspectPicker(w: Words, files: List<Suspect>, picked: Long?, suggested: Long? = null, size: Dp = 66.dp, onPick: (Suspect) -> Unit) {
    Flow(gap = 8.dp) {
        for (s in files) {
            val on = s.id == picked
            Column(
                Modifier.width(size).clickable { onPick(s) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Portrait(s, Modifier.fillMaxWidth().then(
                    if (on) Modifier.border(3.dp, C.red) else if (s.id == suggested) Modifier.border(1.5.dp, C.paper) else Modifier,
                ))
                Text(
                    w.alias(s), style = T.TypeS.copy(color = if (on) C.paper else C.smoke, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}
