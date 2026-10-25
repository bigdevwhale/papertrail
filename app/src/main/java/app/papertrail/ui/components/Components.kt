package app.papertrail.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Film grain: a small tile of noise, drawn once and repeated over everything. */
object Grain {
    val tile: ImageBitmap by lazy {
        val n = 128
        val r = Random(42)
        val px = IntArray(n * n) {
            val v = r.nextInt(256)
            val a = if (r.nextFloat() < .5f) r.nextInt(10, 30) else 0
            (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val brush by lazy { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
}

/** The dark office: night, grain, and the shadow of the blinds falling across from the window. */
fun Modifier.noir(blinds: Boolean = true, shift: Float = 0f) = this.background(C.night).drawBehind {
    if (blinds) {
        val w = size.width; val h = size.height
        val slat = 26.dp.toPx()
        rotate(-24f, Offset(w * .2f, h * .1f)) {
            var y = -h * .4f + shift * slat * 2
            var i = 0
            while (y < h * 1.4f) {
                val fade = (1f - (y / (h * 1.3f))).coerceIn(.15f, 1f)
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, C.lamp.copy(alpha = .055f * fade), C.lamp.copy(alpha = .03f * fade), Color.Transparent), startX = -w * .2f, endX = w * 1.3f),
                    Offset(-w * .4f, y), Size(w * 1.9f, slat * .55f),
                )
                y += slat; i++
            }
        }
    }
    drawRect(Grain.brush)
    // Vignette.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)), center = Offset(size.width * .5f, size.height * .38f), radius = size.maxDimension * .78f))
}

/** Old paper with its grain, for notes, cards and the newspaper. */
fun Modifier.paper(color: Color = C.paper) = this.background(color).drawWithContent {
    drawRect(Grain.brush, alpha = .9f)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, C.cork.copy(alpha = .18f)), center = center, radius = size.maxDimension * .75f))
    drawContent()
}

enum class Look { RED, PAPER, GHOST }

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, look: Look = Look.RED, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    val (bg, fg) = when (look) {
        Look.RED -> C.red to C.paperHi
        Look.PAPER -> C.paper to C.ink
        Look.GHOST -> Color.Transparent to C.paper
    }
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) bg else C.line)
            .then(if (look == Look.GHOST) Modifier.border(1.dp, if (enabled) C.fog else C.line, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = T.Button.copy(color = if (enabled) fg else C.smoke), textAlign = TextAlign.Center) }
}

@Composable
fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier.clip(shape).background(if (on) C.paper else Color.Transparent).border(1.dp, if (on) C.paper else C.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
    ) { Text(text, style = T.TypeS.copy(color = if (on) C.ink else C.fog)) }
}

/** A sheet of paper on the desk. */
@Composable
fun Note(modifier: Modifier = Modifier, color: Color = C.paper, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .drawBehind { drawRect(Color.Black.copy(alpha = .45f), Offset(3.dp.toPx(), 4.dp.toPx()), size) }
            .paper(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/** A dark card on the desk, for lists. */
@Composable
fun Slab(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(C.desk).border(1.dp, C.line, RoundedCornerShape(3.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.smoke) =
    Text(text.uppercase(), style = T.Label.copy(color = color), modifier = modifier.padding(top = 18.dp, bottom = 6.dp))

@Composable
fun Gap(h: Int = 10) = Spacer(Modifier.height(h.dp))

@Composable
fun Flow(modifier: Modifier = Modifier, gap: Dp = 6.dp, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) { content() }
}

/** A typed field on paper. [number] asks for the digits keyboard. */
@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, number: Boolean = false, big: Boolean = false, max: Int = 120) {
    val style: TextStyle = if (big) T.Big.copy(fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp)) else T.Type
    Box(modifier.fillMaxWidth().paper(C.paperHi).drawBehind {
        drawLine(C.inkSoft.copy(alpha = .5f), Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
    }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, style = style.copy(color = C.inkSoft.copy(alpha = .6f)))
        BasicTextField(
            value, { onChange(it.take(max)) }, Modifier.fillMaxWidth(), textStyle = style, singleLine = true, cursorBrush = SolidColor(C.red),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** The title of a tab, typed on the dark. */
@Composable
fun TopBar(title: String, sub: String? = null, end: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.Title)
            if (sub != null) Text(sub, style = T.Small)
        }
        end()
    }
}

/** A full-screen folder over the office, with a title and a way back. */
@Composable
fun Sheet(title: String, onClose: () -> Unit, closeLabel: String, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.shade).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(8.dp).clip(RoundedCornerShape(4.dp)).noir(blinds = false).border(1.dp, C.line, RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = T.Heading, modifier = Modifier.weight(1f))
                Text(closeLabel.uppercase(), style = T.Button.copy(color = C.smoke), modifier = Modifier.clip(RoundedCornerShape(3.dp)).clickable(onClick = onClose).padding(10.dp))
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** A rubber stamp, a little crooked, in red ink. */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, color: Color = C.red, angle: Float = -8f, small: Boolean = false) {
    Box(
        modifier.rotate(angle).border(if (small) 1.5.dp else 2.dp, color.copy(alpha = .85f), RoundedCornerShape(3.dp))
            .padding(horizontal = if (small) 5.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
    ) {
        Text(
            text.uppercase(), maxLines = 1, softWrap = false,
            style = T.Button.copy(
                color = color.copy(alpha = .85f), letterSpacing = androidx.compose.ui.unit.TextUnit(if (small) 1f else 2f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontSize = androidx.compose.ui.unit.TextUnit(if (small) 10f else 14f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )
    }
}

/** A pushpin's head, for notes pinned to the board. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.pin(at: Offset, r: Float, color: Color = C.red) {
    drawCircle(Color.Black.copy(alpha = .4f), r, at + Offset(r * .35f, r * .45f))
    drawCircle(color, r, at)
    drawCircle(Color.White.copy(alpha = .45f), r * .35f, at - Offset(r * .3f, r * .3f))
}

/** A red thread between two points, sagging a little. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.thread(a: Offset, b: Offset, w: Float, sag: Float = .08f) {
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2 + (b - a).getDistance() * sag)
    val p = Path().apply { moveTo(a.x, a.y); quadraticTo(mid.x, mid.y, b.x, b.y) }
    drawPath(p, Color.Black.copy(alpha = .35f), style = Stroke(w * 1.2f))
    drawPath(p, C.red, style = Stroke(w))
}

/** Short messages at the bottom of the screen, typed on a slip. */
class Toaster {
    var text by mutableStateOf<String?>(null)
    var seq by mutableStateOf(0)
    fun show(s: String) { text = s; seq++ }
}

val LocalToast = compositionLocalOf { Toaster() }

@Composable
fun BoxScope.ToastHost(t: Toaster) {
    LaunchedEffect(t.seq) { if (t.text != null) { delay(3400); t.text = null } }
    val s = t.text ?: return
    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 84.dp)) {
        Text(s, style = T.Type.copy(textAlign = TextAlign.Center), modifier = Modifier.paper(C.paperHi).clickable { t.text = null }.padding(horizontal = 16.dp, vertical = 11.dp))
    }
}
