package app.papertrail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.papertrail.R

/** Black and white film, old paper, lamplight, and one colour only: the red of the thread and the stamp. */
@Immutable
object C {
    val night = Color(0xFF151413)
    val desk = Color(0xFF221F1C)
    val deskHi = Color(0xFF2E2A26)
    val line = Color(0xFF3A3531)
    val smoke = Color(0xFF9C958A)
    val fog = Color(0xFFC9C2B6)
    val paper = Color(0xFFE8E1D0)
    val paperHi = Color(0xFFF3EEE2)
    val paperDim = Color(0xFFCFC6B1)
    val ink = Color(0xFF1B1916)
    val inkSoft = Color(0xFF5A544B)
    val red = Color(0xFFB3262B)
    val redDeep = Color(0xFF7E1A1E)
    val lamp = Color(0xFFF2DFB0)
    val cork = Color(0xFF6E5A45)
    val corkHi = Color(0xFF85705A)
    val brass = Color(0xFFB59A5E)
    val steel = Color(0xFF4A4744)
    val steelHi = Color(0xFF6B6763)
    val shade = Color(0xCC0B0A09)
}

val Serif = FontFamily(
    Font(R.font.pt_serif_regular, FontWeight.Normal),
    Font(R.font.pt_serif_bold, FontWeight.Bold),
    Font(R.font.pt_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
/** The typewriter. */
val Mono = FontFamily(Font(R.font.pt_mono, FontWeight.Normal))
/** The detective's pencil on the board. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))

object T {
    val Title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, color = C.paper)
    val Heading = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, color = C.paper)
    val Body = TextStyle(fontFamily = Serif, fontSize = 15.sp, lineHeight = 21.sp, color = C.paper)
    val Small = TextStyle(fontFamily = Serif, fontSize = 13.sp, lineHeight = 18.sp, color = C.smoke)
    val Italic = Body.copy(fontStyle = FontStyle.Italic)
    val Type = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp, color = C.ink)
    val TypeS = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp, color = C.inkSoft)
    val Label = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp, color = C.smoke)
    val Button = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 1.sp)
    val Pencil = TextStyle(fontFamily = Hand, fontSize = 22.sp, lineHeight = 24.sp, color = C.ink)
    val Big = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = C.ink)
}

@Composable
fun TrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.red, onPrimary = C.paperHi, background = C.night, surface = C.desk, onSurface = C.paper,
            onBackground = C.paper, outline = C.line, error = C.red,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}
