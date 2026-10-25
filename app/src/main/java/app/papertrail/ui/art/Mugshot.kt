package app.papertrail.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import app.papertrail.domain.Cast
import app.papertrail.domain.Hat
import app.papertrail.domain.Mark
import app.papertrail.domain.Suspect
import app.papertrail.ui.components.Grain
import kotlin.math.abs

/**
 * A police photo: the height chart behind, a face lit hard from the left by a bare bulb so half of it
 * sinks into shadow, a hat whose brim darkens the eyes, and one thing that gives the suspect away.
 * Drawn in a 100×120 box, in greys only.
 */
object Mugshot {
    enum class Shape { OVAL, SQUARE, LONG, ROUND, HEART }
    enum class Hair { NONE, SLICK, PARTED, CURLY, WAVY, BUN, CROP }
    enum class Brows { FLAT, ARCHED, ANGRY, BUSHY, THIN }
    enum class Eyes { ROUND, NARROW, HEAVY, LASH }
    enum class Nose { STRAIGHT, HOOK, BUTTON, BROAD }
    enum class Mouth { FLAT, SMIRK, FROWN, SMILE, LIPS }
    enum class Beard { NONE, STUBBLE, FULL, CHOPS, PENCIL, WALRUS }

    data class Face(
        val shape: Shape, val hair: Hair, val brows: Brows, val eyes: Eyes, val nose: Nose, val mouth: Mouth,
        val beard: Beard = Beard.NONE, val lines: Boolean = false, val tone: Int = 1, val hairTone: Int = 0,
    )

    data class Look(val hat: Hat, val mark: Mark, val face: Face, val coat: Int = 0)

    /** The house characters' faces, one by one. */
    private val FACES = mapOf(
        "courier" to Face(Shape.ROUND, Hair.CURLY, Brows.FLAT, Eyes.ROUND, Nose.BUTTON, Mouth.SMIRK, Beard.STUBBLE, tone = 0),
        "boxman" to Face(Shape.LONG, Hair.SLICK, Brows.THIN, Eyes.NARROW, Nose.HOOK, Mouth.FLAT, lines = true, tone = 1),
        "barista" to Face(Shape.OVAL, Hair.PARTED, Brows.ARCHED, Eyes.ROUND, Nose.STRAIGHT, Mouth.SMILE, Beard.WALRUS, tone = 0, hairTone = 1),
        "maitre" to Face(Shape.LONG, Hair.SLICK, Brows.ARCHED, Eyes.HEAVY, Nose.STRAIGHT, Mouth.SMIRK, Beard.PENCIL, tone = 0),
        "cabbie" to Face(Shape.SQUARE, Hair.CROP, Brows.ANGRY, Eyes.NARROW, Nose.BROAD, Mouth.FROWN, Beard.STUBBLE, tone = 2),
        "dandy" to Face(Shape.HEART, Hair.WAVY, Brows.ARCHED, Eyes.HEAVY, Nose.STRAIGHT, Mouth.SMIRK, tone = 0, hairTone = 1),
        "croupier" to Face(Shape.OVAL, Hair.SLICK, Brows.ANGRY, Eyes.NARROW, Nose.HOOK, Mouth.FLAT, tone = 1),
        "grocer" to Face(Shape.ROUND, Hair.NONE, Brows.BUSHY, Eyes.ROUND, Nose.BROAD, Mouth.SMILE, Beard.WALRUS, lines = true, tone = 1, hairTone = 2),
        "landlord" to Face(Shape.SQUARE, Hair.PARTED, Brows.BUSHY, Eyes.HEAVY, Nose.HOOK, Mouth.FROWN, Beard.CHOPS, lines = true, tone = 1, hairTone = 2),
        "doc" to Face(Shape.LONG, Hair.PARTED, Brows.FLAT, Eyes.ROUND, Nose.STRAIGHT, Mouth.FLAT, Beard.NONE, lines = true, tone = 0, hairTone = 2),
        "conductor" to Face(Shape.ROUND, Hair.CROP, Brows.BUSHY, Eyes.ROUND, Nose.BROAD, Mouth.FLAT, Beard.FULL, tone = 1),
        "operator" to Face(Shape.HEART, Hair.BUN, Brows.THIN, Eyes.LASH, Nose.BUTTON, Mouth.LIPS, tone = 0),
    )
    private val COATS = mapOf("courier" to 1, "cabbie" to 1, "grocer" to 2, "doc" to 2, "conductor" to 3, "operator" to 3)

    fun look(s: Suspect?): Look {
        val c = Cast[s?.preset]
        if (c != null) return Look(c.hat, c.mark, FACES.getValue(c.key), COATS[c.key] ?: 0)
        // The detective's own suspects: a face put together from the seed.
        val f = abs(s?.face ?: 0)
        fun <T> pick(list: List<T>, salt: Int) = list[(f / salt) % list.size]
        val hair = pick(Hair.entries.filter { it != Hair.BUN }, 3)
        return Look(
            pick(listOf(Hat.NONE, Hat.FEDORA, Hat.BOWLER, Hat.FLAT, Hat.CAP, Hat.NONE), 1),
            pick(listOf(Mark.NONE, Mark.TIE, Mark.GLASSES, Mark.SCAR, Mark.CIGAR, Mark.BOW, Mark.MONOCLE, Mark.CUP), 7),
            Face(pick(Shape.entries, 11), hair, pick(Brows.entries, 13), pick(Eyes.entries.filter { it != Eyes.LASH }, 17),
                pick(Nose.entries, 19), pick(Mouth.entries.filter { it != Mouth.LIPS }, 23), pick(Beard.entries, 29), (f / 31) % 2 == 0, (f / 37) % 3, (f / 41) % 3),
            (f / 43) % 4,
        )
    }

    fun DrawScope.mugshot(s: Suspect?, wanted: Boolean = false) = draw(look(s), wanted)

    /** Path in the 100×120 units. */
    private class P {
        val p = Path()
        fun m(x: Float, y: Float) = p.moveTo(x, y)
        fun l(x: Float, y: Float) = p.lineTo(x, y)
        fun q(x1: Float, y1: Float, x: Float, y: Float) = p.quadraticTo(x1, y1, x, y)
        fun c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = p.cubicTo(x1, y1, x2, y2, x, y)
        fun z() = p.close()
    }

    private fun path(b: P.() -> Unit) = P().apply(b).p

    private val INK = Color(0xFF14120F)
    private val CHART = Color(0xFF3B3833)

    private class Geo(val cw: Float, val jw: Float, val top: Float, val chin: Float, val cheekY: Float, val jawY: Float)

    private fun geo(s: Shape) = when (s) {
        Shape.OVAL -> Geo(15.5f, 10f, 29f, 77f, 50f, 67f)
        Shape.SQUARE -> Geo(16.5f, 15f, 30f, 76f, 50f, 70f)
        Shape.LONG -> Geo(14f, 9.5f, 27f, 80f, 50f, 70f)
        Shape.ROUND -> Geo(17.5f, 13f, 30f, 75f, 52f, 65f)
        Shape.HEART -> Geo(16.5f, 7.5f, 29f, 77f, 48f, 65f)
    }

    private fun head(g: Geo) = path {
        m(50f, g.top)
        c(50f + g.cw * .85f, g.top, 50f + g.cw, g.top + 9f, 50f + g.cw, g.cheekY)
        c(50f + g.cw, g.jawY - 5f, 50f + g.jw + 3f, g.jawY, 50f + g.jw, g.jawY + 2f)
        q(50f + g.jw * .55f, g.chin, 50f, g.chin)
        q(50f - g.jw * .55f, g.chin, 50f - g.jw, g.jawY + 2f)
        c(50f - g.jw - 3f, g.jawY, 50f - g.cw, g.jawY - 5f, 50f - g.cw, g.cheekY)
        c(50f - g.cw, g.top + 9f, 50f - g.cw * .85f, g.top, 50f, g.top)
        z()
    }

    fun DrawScope.draw(l: Look, wanted: Boolean = false) {
        val k = minOf(size.width / 100f, size.height / 120f)
        val ox = (size.width - 100 * k) / 2; val oy = (size.height - 120 * k) / 2
        clipRect(ox, oy, ox + 100 * k, oy + 120 * k) {
            withTransform({ translate(ox, oy); scale(k, k, Offset.Zero) }) { scene(l) }
            drawRect(Grain.brush, Offset(ox, oy), Size(100 * k, 120 * k))
            if (wanted) drawRect(Color.Black.copy(alpha = .15f), Offset(ox, oy), Size(100 * k, 120 * k))
        }
    }

    private fun DrawScope.scene(l: Look) {
        val f = l.face
        val g = geo(f.shape)
        val skin = listOf(Color(0xFFC9C2B6), Color(0xFFB0A89C), Color(0xFF948C80))[f.tone]
        val skinLo = lerp(skin, INK, .55f)
        val skinMid = lerp(skin, INK, .25f)
        val hairC = listOf(Color(0xFF1A1714), Color(0xFF4A443D), Color(0xFF9A948B))[f.hairTone]

        // The wall: lit from the left, the height chart, the bulb's falloff.
        drawRect(Brush.radialGradient(listOf(Color(0xFFDDD8CE), Color(0xFF8E8980)), Offset(30f, 35f), 100f), Offset.Zero, Size(100f, 120f))
        for (i in 0..12) {
            val y = 8f + i * 9f
            val major = i % 2 == 0
            drawLine(CHART.copy(alpha = if (major) .5f else .22f), Offset(0f, y), Offset(if (major) 100f else 10f, y), if (major) .6f else .4f)
            if (major) drawLine(CHART.copy(alpha = .4f), Offset(90f, y), Offset(100f, y), 1.2f)
        }
        // The figure's own shadow on the wall, thrown right and soft at the edges.
        val shoulders = shoulders()
        val neck = neck(g)
        val h = head(g)
        val silhouette = Path().apply { addPath(shoulders); addPath(neck); addPath(h); addPath(hatShape(l.hat, g)); addPath(hairShape(f, g)) }
        withTransform({ translate(7f, 2.5f) }) { soft(silhouette, Color.Black.copy(alpha = .42f), 2.8f) }

        body(l, shoulders)

        // Neck: lit on the left, the jaw's shadow falling on it, the tendons faint.
        drawPath(neck, Brush.horizontalGradient(listOf(skinMid, lerp(skinLo, INK, .1f)), 41f, 59f))
        clipPath(neck) {
            soft(path { m(38f, g.chin - 10f); q(50f, g.chin + 7f, 62f, g.chin - 10f); l(62f, g.chin - 12f); l(38f, g.chin - 12f); z() }, Color.Black.copy(alpha = .55f), 2.2f)
            drawPath(path { m(45f, g.chin + 2f); q(46f, 82f, 48.5f, 88f) }, skinLo.copy(alpha = .5f), style = Stroke(.6f))
            drawPath(path { m(55f, g.chin + 2f); q(54f, 82f, 51.5f, 88f) }, INK.copy(alpha = .3f), style = Stroke(.6f))
        }

        ears(g, skin, skinLo)
        hairBack(f, g, hairC)

        drawPath(h, Brush.horizontalGradient(listOf(lerp(skin, Color.White, .1f), skin, skinMid, skinLo), 50f - g.cw, 50f + g.cw))
        clipPath(h) {
            // The bulb's highlight on the forehead and the lit cheek.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .28f), Color.Transparent), Offset(44f, g.top + 9f), 11f), 11f, Offset(44f, g.top + 9f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .18f), Color.Transparent), Offset(42f, g.cheekY + 6f), 8f), 8f, Offset(42f, g.cheekY + 6f))
            // Under the cheekbone and along the jaw, the face turns away.
            soft(path { m(50f - g.cw + 1f, g.cheekY + 7f); q(42f, g.cheekY + 11f, 45f, g.jawY + 2f); l(50f - g.jw, g.jawY + 3f); z() }, Color.Black.copy(alpha = .14f), 2.5f)
            soft(path { m(50f - g.jw - 2f, g.jawY + 1f); q(50f, g.chin + 4f, 50f + g.jw + 2f, g.jawY + 1f); l(50f + g.jw + 2f, g.chin + 4f); l(50f - g.jw - 2f, g.chin + 4f); z() }, Color.Black.copy(alpha = .3f), 2f)
            features(f, g, skin, skinLo)
            // The terminator: the right half of the face falls away from the bulb, soft-edged.
            val sh = path {
                m(53f, g.top - 3f); c(60f, g.top + 10f, 54.5f, 42f, 56.5f, 49f); c(58f, 54f, 54f, 58f, 55.5f, 63f)
                c(57f, 68f, 54f, g.chin - 4f, 52f, g.chin + 3f); l(82f, g.chin + 3f); l(82f, g.top - 3f); z()
            }
            soft(sh, Color(0xFF0E0C0A).copy(alpha = .62f), 1.1f)
            // Light bouncing back off the collar onto the dark cheek.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .08f), Color.Transparent), Offset(50f + g.cw, g.jawY), 7f), 7f, Offset(50f + g.cw, g.jawY))
            // A rim of light along the left edge.
            drawPath(h, Brush.horizontalGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent), 50f - g.cw, 46f), style = Stroke(1.4f))
        }
        hairFront(f, g, hairC)
        beard(f, g, hairC)
        mark(l.mark, g, skin)
        hat(l.hat, g)
        // A brim's shadow across the eyes, the oldest trick of the genre.
        if (l.hat in setOf(Hat.FEDORA, Hat.BOWLER, Hat.FLAT, Hat.CAP, Hat.CONDUCTOR, Hat.VISOR)) clipPath(h) {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent), g.top + 10f, 54f), Offset(30f, g.top), Size(40f, 54f - g.top))
        }

        // The number board.
        drawRect(Color(0xFF151311), Offset(28f, 104f), Size(44f, 14f))
        drawRect(Color(0xFFD8D2C6), Offset(30f, 106f), Size(40f, 10f), style = Stroke(.5f))
        for (i in 0..5) drawRect(Color(0xFFD8D2C6), Offset(34f + i * 5.5f, 109f), Size(3f, 4f))
    }

    private fun shoulders() = path {
        m(6f, 122f); c(6f, 100f, 14f, 91f, 30f, 88f); c(36f, 87f, 39f, 85f, 41.5f, 82f)
        l(58.5f, 82f); c(61f, 85f, 64f, 87f, 70f, 88f); c(86f, 91f, 94f, 100f, 94f, 122f); z()
    }

    private fun neck(g: Geo) = path {
        m(42.5f, g.chin - 9f); c(42.5f, 76f, 41.5f, 82f, 40f, 87f); q(50f, 91f, 60f, 87f); c(58.5f, 82f, 57.5f, 76f, 57.5f, g.chin - 9f); z()
    }

    private fun DrawScope.ears(g: Geo, skin: Color, skinLo: Color) {
        val y = g.cheekY - 6f
        val lx = 50f - g.cw
        val left = path { m(lx + 1f, y); c(lx - 4.5f, y - 1.5f, lx - 5f, y + 9f, lx + .5f, y + 11f); z() }
        drawPath(left, Brush.horizontalGradient(listOf(lerp(skin, Color.White, .1f), skinLo), lx - 5f, lx + 1f))
        drawPath(path { m(lx - .2f, y + 2f); c(lx - 3f, y + 2f, lx - 3f, y + 7f, lx - .5f, y + 8f) }, skinLo, style = Stroke(.7f))
        val rx = 50f + g.cw
        drawPath(path { m(rx - 1f, y); c(rx + 4.5f, y - 1.5f, rx + 5f, y + 9f, rx - .5f, y + 11f); z() }, lerp(skinLo, INK, .2f))
    }

    private fun DrawScope.body(l: Look, shoulders: Path) {
        val coat = listOf(Color(0xFF2A2724), Color(0xFF3A3632), Color(0xFFE2DED6), Color(0xFF23262B))[l.coat]
        val coatLo = lerp(coat, INK, .5f)
        drawPath(shoulders, Brush.horizontalGradient(listOf(lerp(coat, Color.White, .14f), coat, coatLo, lerp(coatLo, INK, .3f)), 6f, 94f))
        clipPath(shoulders) {
            // The top of the shoulders catches the bulb; the chest falls into shade below the collar.
            soft(path { m(8f, 100f); c(12f, 90f, 24f, 88f, 34f, 88f); l(34f, 92f); c(22f, 93f, 14f, 98f, 12f, 108f); z() }, Color.White.copy(alpha = .14f), 2.5f)
            // Folds of cloth.
            drawPath(path { m(22f, 122f); q(26f, 108f, 34f, 100f) }, Color.Black.copy(alpha = .25f), style = Stroke(.8f))
            drawPath(path { m(78f, 122f); q(74f, 108f, 66f, 100f) }, Color.Black.copy(alpha = .3f), style = Stroke(.8f))
            drawPath(path { m(16f, 112f); q(20f, 104f, 28f, 98f) }, Color.White.copy(alpha = .06f), style = Stroke(.6f))
        }
        // Shirt, in the V of the coat.
        drawPath(path { m(41f, 84f); l(50f, 104f); l(59f, 84f); z() }, Brush.horizontalGradient(listOf(Color(0xFFE6E0D5), Color(0xFFB9B3A8)), 41f, 59f))
        soft(path { m(43f, 84f); q(50f, 92f, 57f, 84f); l(57f, 86f); q(50f, 95f, 43f, 86f); z() }, Color.Black.copy(alpha = .3f), 1.2f)
        // Lapels: the left lit, the right in shade, each with its notch.
        drawPath(path { m(37f, 85f); l(41f, 84f); l(47.5f, 110f); l(41f, 97f); l(37.5f, 95f); l(40f, 91f); z() }, lerp(coat, Color.White, .06f))
        drawPath(path { m(37f, 85f); l(41f, 84f); l(47.5f, 110f) }, Color.Black.copy(alpha = .35f), style = Stroke(.5f))
        drawPath(path { m(63f, 85f); l(59f, 84f); l(52.5f, 110f); l(59f, 97f); l(62.5f, 95f); l(60f, 91f); z() }, lerp(coatLo, INK, .35f))
        // Collar points.
        drawPath(path { m(42f, 83.5f); l(46.5f, 92f); l(50f, 88f); z() }, Color(0xFFF1ECE2))
        drawPath(path { m(58f, 83.5f); l(53.5f, 92f); l(50f, 88f); z() }, Color(0xFFADA79C))
        if (l.coat == 2) drawLine(Color(0xFFB0AA9F), Offset(30f, 100f), Offset(30f, 120f), .6f)
        if (l.coat == 1) {
            drawLine(Color(0xFF75706A), Offset(50f, 106f), Offset(50f, 122f), .8f)
            for (i in 0..4) drawLine(Color(0xFF8A847C), Offset(49.2f, 108f + i * 3f), Offset(50.8f, 108f + i * 3f), .4f)
        }
        if (l.coat == 0) for (y in listOf(108f, 116f)) drawCircle(Color(0xFF0F0E0C), .9f, Offset(51.5f, y))
    }

    /** A rough outline of the hat, for the shadow on the wall. */
    private fun hatShape(h: Hat, g: Geo): Path {
        val t = g.top
        return when (h) {
            Hat.NONE, Hat.NURSE, Hat.VISOR -> Path()
            Hat.FEDORA -> path { m(18f, t + 11f); q(50f, t + 2f, 82f, t + 11f); q(50f, t + 17f, 18f, t + 11f); z(); m(33f, t + 10f); c(31f, t - 12f, 69f, t - 12f, 67f, t + 10f); z() }
            Hat.BOWLER -> path { m(26f, t + 10f); q(50f, t + 4f, 74f, t + 10f); q(50f, t + 14f, 26f, t + 10f); z(); m(33f, t + 8f); c(33f, t - 14f, 67f, t - 14f, 67f, t + 8f); z() }
            Hat.TOP -> path { m(25f, t + 9f); q(50f, t + 3f, 75f, t + 9f); q(50f, t + 13f, 25f, t + 9f); z(); m(35f, t - 22f); l(65f, t - 22f); l(65f, t + 8f); l(35f, t + 8f); z() }
            Hat.CHEF -> path { m(35f, t + 8f); l(35f, t - 14f); c(35f, t - 24f, 70f, t - 24f, 70f, t - 10f); l(65f, t + 8f); z() }
            else -> path { m(31f, t + 11f); c(29f, t - 7f, 69f, t - 7f, 70f, t + 9f); z(); m(31f, t + 9f); q(22f, t + 9f, 18f, t + 13f); q(30f, t + 14f, 40f, t + 11f); z() }
        }
    }

    /** Hair that stands out beyond the head, for the shadow too. */
    private fun hairShape(f: Face, g: Geo): Path = when (f.hair) {
        Hair.BUN -> Path().apply { addOval(Rect(Offset(50f, g.top - 3f), 7.5f)) }
        Hair.CURLY, Hair.WAVY -> Path().apply { addOval(Rect(50f - g.cw - 4f, g.top - 5f, 50f + g.cw + 4f, g.cheekY + 4f)) }
        else -> Path()
    }

    /** A filled path with its edge blurred by [blur] units: soft shadows and light. */
    private fun DrawScope.soft(p: Path, color: Color, blur: Float) {
        drawIntoCanvas { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.drawPath(p.asAndroidPath(), paint)
        }
    }

    private fun DrawScope.features(f: Face, g: Geo, skin: Color, skinLo: Color) {
        val eyeY = g.top + (g.chin - g.top) * .44f
        val noseY = g.top + (g.chin - g.top) * .66f
        val mouthY = g.top + (g.chin - g.top) * .81f
        // Sockets.
        soft(Path().apply { addOval(Rect(37.5f, eyeY - 4.5f, 48.5f, eyeY + 3f)) }, skinLo.copy(alpha = .55f), 1.6f)
        soft(Path().apply { addOval(Rect(51.5f, eyeY - 4.5f, 62.5f, eyeY + 3f)) }, skinLo.copy(alpha = .65f), 1.6f)
        eyes(f.eyes, eyeY)
        brows(f.brows, eyeY - 5.5f)
        nose(f.nose, eyeY, noseY, skin, skinLo)
        mouth(f.mouth, mouthY)
        if (f.lines) {
            drawPath(path { m(44f, noseY + 1f); q(41f, mouthY - 2f, 42.5f, mouthY + 2f) }, skinLo.copy(alpha = .6f), style = Stroke(.6f))
            drawPath(path { m(56f, noseY + 1f); q(59f, mouthY - 2f, 57.5f, mouthY + 2f) }, skinLo.copy(alpha = .6f), style = Stroke(.6f))
            drawLine(skinLo.copy(alpha = .4f), Offset(44f, g.top + 9f), Offset(56f, g.top + 9f), .5f)
            drawLine(skinLo.copy(alpha = .35f), Offset(45f, g.top + 11.5f), Offset(55f, g.top + 11.5f), .5f)
        }
        // Under the chin.
        drawOval(Color.Black.copy(alpha = .12f), Offset(42f, g.chin - 5f), Size(16f, 6f))
    }

    private fun DrawScope.eyes(e: Eyes, y: Float) {
        for ((cx, lit) in listOf(43f to true, 57f to false)) {
            val w = when (e) { Eyes.NARROW -> 6.5f; Eyes.ROUND -> 6f; else -> 6.5f }
            val h = when (e) { Eyes.NARROW -> 1.6f; Eyes.ROUND -> 3.2f; Eyes.HEAVY -> 2.2f; Eyes.LASH -> 3f }
            val white = if (lit) Color(0xFFE6E2DA) else Color(0xFF8F897F)
            val eye = path { m(cx - w / 2, y); q(cx, y - h, cx + w / 2, y); q(cx, y + h * .8f, cx - w / 2, y); z() }
            drawPath(eye, white)
            clipPath(eye) { drawCircle(INK, 1.7f, Offset(cx + .3f, y + .1f)) }
            if (lit) drawCircle(Color.White, .45f, Offset(cx - .3f, y - .6f))
            // Upper lid; heavy lids droop over half the eye.
            if (e == Eyes.HEAVY) drawPath(path { m(cx - w / 2 - .3f, y - .2f); q(cx, y - h - .6f, cx + w / 2 + .3f, y - .2f); q(cx, y - .4f, cx - w / 2 - .3f, y - .2f); z() }, lerp(white, INK, .6f))
            drawPath(path { m(cx - w / 2, y); q(cx, y - h, cx + w / 2, y) }, INK, style = Stroke(if (e == Eyes.LASH) 1f else .7f, cap = StrokeCap.Round))
            if (e == Eyes.LASH) for (i in 0..2) {
                val x = cx + w / 2 - .4f - i * .9f
                drawLine(INK, Offset(x, y - h * .55f + i * .15f), Offset(x + 1f, y - h * .55f - 1.2f), .45f)
            }
            drawLine(INK.copy(alpha = .35f), Offset(cx - w / 2 + 1f, y + h * .75f), Offset(cx + w / 2 - 1f, y + h * .75f), .4f)
        }
    }

    private fun DrawScope.brows(b: Brows, y: Float) {
        for (side in listOf(-1f, 1f)) {
            val inner = 50f + side * 3f
            val outer = 50f + side * 11f
            val (wd, p) = when (b) {
                Brows.FLAT -> 1.2f to path { m(inner, y); q((inner + outer) / 2, y - .6f, outer, y + .2f) }
                Brows.ARCHED -> 1f to path { m(inner, y + .3f); q((inner + outer) / 2, y - 3f, outer, y + .8f) }
                Brows.ANGRY -> 1.5f to path { m(inner, y + 1.6f); q((inner + outer) / 2, y - .6f, outer, y - 1.2f) }
                Brows.BUSHY -> 2.6f to path { m(inner, y + .3f); q((inner + outer) / 2, y - 1.6f, outer, y + .4f) }
                Brows.THIN -> .6f to path { m(inner, y); q((inner + outer) / 2, y - 2.4f, outer, y + .6f) }
            }
            drawPath(p, INK.copy(alpha = if (side < 0) .9f else .75f), style = Stroke(wd, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.nose(n: Nose, eyeY: Float, y: Float, skin: Color, skinLo: Color) {
        val w = when (n) { Nose.BROAD -> 4.6f; Nose.BUTTON -> 2.8f; else -> 3.4f }
        // The ridge catches the light; the right side drops into shadow.
        val ridge = when (n) {
            Nose.HOOK -> path { m(51f, eyeY + 2f); c(54.5f, eyeY + 7f, 55f, y - 3f, 52.5f, y) }
            Nose.BUTTON -> path { m(51f, eyeY + 5f); q(52.5f, y - 2f, 52f, y) }
            else -> path { m(51f, eyeY + 2f); q(53f, y - 4f, 53f, y) }
        }
        // The nose throws its own shadow to the right, down onto the lip.
        soft(path { m(51.5f, eyeY + 3f); q(55f, y - 4f, 54f, y + 1f); q(56f, y + 4f, 52f, y + 3.5f); z() }, INK.copy(alpha = .4f), 1.3f)
        drawPath(ridge, skinLo.copy(alpha = .8f), style = Stroke(.8f, cap = StrokeCap.Round))
        drawLine(lerp(skin, Color.White, .35f), Offset(49.5f, eyeY + 3f), Offset(49.8f, y - 3f), .8f)
        // Nostrils and the shadow under the tip.
        drawOval(skinLo, Offset(50f - w, y - .4f), Size(w * 2, 2.2f))
        drawCircle(INK.copy(alpha = .6f), .7f, Offset(48.3f - (w - 3f) * .4f, y + .6f))
        drawCircle(INK.copy(alpha = .7f), .7f, Offset(51.8f + (w - 3f) * .4f, y + .6f))
    }

    private fun DrawScope.mouth(m: Mouth, y: Float) {
        val st = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (m) {
            Mouth.FLAT -> drawPath(path { m(45f, y); q(50f, y + .5f, 55f, y - .2f) }, INK, style = st)
            Mouth.SMIRK -> drawPath(path { m(45f, y + .4f); q(50f, y + .8f, 55.5f, y - 1.6f) }, INK, style = st)
            Mouth.FROWN -> drawPath(path { m(45f, y + 1.2f); q(50f, y - 1f, 55f, y + 1.2f) }, INK, style = st)
            Mouth.SMILE -> drawPath(path { m(44.5f, y - .8f); q(50f, y + 2.8f, 55.5f, y - .8f) }, INK, style = st)
            Mouth.LIPS -> {
                drawPath(path { m(45f, y); q(47.5f, y - 2.2f, 50f, y - 1f); q(52.5f, y - 2.2f, 55f, y); q(50f, y + 3.2f, 45f, y); z() }, Color(0xFF3A302B))
                drawLine(INK, Offset(45.5f, y + .1f), Offset(54.5f, y + .1f), .5f)
                drawCircle(Color.White.copy(alpha = .4f), .6f, Offset(48f, y + 1.1f))
            }
        }
        // The lower lip's shade.
        if (m != Mouth.LIPS) drawLine(INK.copy(alpha = .22f), Offset(47.5f, y + 2.2f), Offset(52.5f, y + 2.2f), .9f)
    }

    private fun DrawScope.hairBack(f: Face, g: Geo, c: Color) {
        when (f.hair) {
            Hair.WAVY -> drawPath(path {
                m(50f - g.cw - 2.5f, g.cheekY); c(50f - g.cw - 6f, g.top - 4f, 50f + g.cw + 6f, g.top - 6f, 50f + g.cw + 2.5f, g.cheekY)
                q(50f + g.cw + 3f, g.cheekY + 4f, 50f + g.cw, g.cheekY + 3f); l(50f - g.cw, g.cheekY + 3f)
                q(50f - g.cw - 3f, g.cheekY + 4f, 50f - g.cw - 2.5f, g.cheekY); z()
            }, c)
            Hair.BUN -> {
                drawCircle(c, 7.5f, Offset(50f, g.top - 3f))
                drawPath(path { m(50f - g.cw - 2f, g.cheekY); c(50f - g.cw - 4f, g.top - 4f, 50f + g.cw + 4f, g.top - 4f, 50f + g.cw + 2f, g.cheekY); z() }, c)
            }
            Hair.CURLY -> for (i in 0..10) {
                val a = Math.PI * (1.05 + i * .09)
                drawCircle(c, 4.2f, Offset(50f + (g.cw + 1f) * kotlin.math.cos(a).toFloat(), g.top + 12f + (g.cw + 2f) * kotlin.math.sin(a).toFloat()))
            }
            else -> Unit
        }
    }

    private fun DrawScope.hairFront(f: Face, g: Geo, c: Color) {
        val shine = Color.White.copy(alpha = .22f)
        when (f.hair) {
            Hair.NONE -> drawOval(Color.White.copy(alpha = .3f), Offset(41f, g.top + 2f), Size(7f, 3f))
            Hair.SLICK -> {
                drawPath(path { m(50f - g.cw - .5f, g.cheekY - 6f); c(50f - g.cw, g.top - 3f, 50f + g.cw, g.top - 3f, 50f + g.cw + .5f, g.cheekY - 6f); q(50f + g.cw - 2f, g.top + 6f, 50f, g.top + 6.5f); q(50f - g.cw + 2f, g.top + 6f, 50f - g.cw - .5f, g.cheekY - 6f); z() }, c)
                for (i in 0..3) drawPath(path { m(42f + i * 3f, g.top + 5f); q(46f + i * 3f, g.top - 1f, 56f + i * 2f, g.top + 2f) }, shine, style = Stroke(.5f))
            }
            Hair.PARTED -> {
                drawPath(path { m(50f - g.cw - .5f, g.cheekY - 4f); c(50f - g.cw, g.top - 3f, 50f + g.cw, g.top - 3f, 50f + g.cw + .5f, g.cheekY - 6f); q(50f + g.cw - 3f, g.top + 5f, 44f, g.top + 6f); q(50f - g.cw + 1f, g.top + 8f, 50f - g.cw - .5f, g.cheekY - 4f); z() }, c)
                drawLine(Color.White.copy(alpha = .35f), Offset(43.5f, g.top + 1f), Offset(44f, g.top + 6f), .6f)
                drawPath(path { m(46f, g.top + 4f); q(52f, g.top - 1f, 60f, g.top + 3f) }, shine, style = Stroke(.5f))
            }
            Hair.CURLY -> for (i in 0..6) drawCircle(c, 3.6f, Offset(50f - g.cw + 4f + i * (g.cw * 2 - 8f) / 6f, g.top + 2.5f + (if (i % 2 == 0) 0f else 1.5f)))
            Hair.WAVY -> {
                drawPath(path { m(50f - g.cw - 1f, g.cheekY - 2f); c(50f - g.cw - 2f, g.top - 4f, 50f + g.cw + 2f, g.top - 4f, 50f + g.cw + 1f, g.cheekY - 4f); q(56f, g.top + 2f, 47f, g.top + 9f); q(44f, g.top + 4f, 50f - g.cw - 1f, g.cheekY - 2f); z() }, c)
                drawPath(path { m(42f, g.top + 3f); q(48f, g.top - 2f, 58f, g.top + 1f) }, shine, style = Stroke(.6f))
            }
            Hair.BUN -> {
                drawPath(path { m(50f - g.cw - .5f, g.cheekY - 3f); c(50f - g.cw, g.top - 3f, 50f + g.cw, g.top - 3f, 50f + g.cw + .5f, g.cheekY - 3f); q(50f + g.cw - 3f, g.top + 6f, 50f, g.top + 5f); q(50f - g.cw + 3f, g.top + 6f, 50f - g.cw - .5f, g.cheekY - 3f); z() }, c)
                drawPath(path { m(44f, g.top + 2f); q(50f, g.top - 2f, 57f, g.top + 2f) }, shine, style = Stroke(.5f))
                // An earring catching the bulb.
                drawCircle(Color(0xFFE9E6DF), 1.1f, Offset(50f - g.cw - .5f, g.cheekY + 5f))
            }
            Hair.CROP -> {
                drawPath(path { m(50f - g.cw, g.cheekY - 7f); c(50f - g.cw, g.top - 1f, 50f + g.cw, g.top - 1f, 50f + g.cw, g.cheekY - 7f); q(50f, g.top + 5f, 50f - g.cw, g.cheekY - 7f); z() }, c.copy(alpha = .85f))
            }
        }
    }

    private fun DrawScope.beard(f: Face, g: Geo, c: Color) {
        val mouthY = g.top + (g.chin - g.top) * .81f
        val noseY = g.top + (g.chin - g.top) * .66f
        when (f.beard) {
            Beard.NONE -> Unit
            Beard.STUBBLE -> clipPath(head(g)) {
                for (i in 0 until 70) {
                    val x = 50f - g.jw - 3f + (i * 37 % 100) / 100f * (g.jw * 2 + 6f)
                    val y = mouthY - 3f + (i * 53 % 100) / 100f * (g.chin - mouthY + 4f)
                    drawCircle(c.copy(alpha = .45f), .35f, Offset(x, y))
                }
            }
            Beard.FULL -> drawPath(path {
                m(50f - g.cw + 1f, g.cheekY + 2f); q(50f - g.cw, g.chin + 2f, 50f, g.chin + 5f); q(50f + g.cw, g.chin + 2f, 50f + g.cw - 1f, g.cheekY + 2f)
                q(50f + 6f, mouthY - 2f, 50f, mouthY + 3f); q(50f - 6f, mouthY - 2f, 50f - g.cw + 1f, g.cheekY + 2f); z()
            }, c)
            Beard.CHOPS -> for (s in listOf(-1f, 1f)) drawPath(path {
                m(50f + s * (g.cw - .3f), g.cheekY - 7f); l(50f + s * (g.cw + .4f), g.jawY - 1f); q(50f + s * (g.cw - 1f), g.jawY + 4f, 50f + s * (g.cw - 5f), g.jawY + 1f); l(50f + s * (g.cw - 3.5f), g.cheekY - 5f); z()
            }, c)
            Beard.PENCIL -> drawPath(path { m(45f, mouthY - 2.2f); q(50f, mouthY - 3.5f, 55f, mouthY - 2.2f) }, c, style = Stroke(.7f))
            Beard.WALRUS -> drawPath(path {
                m(43f, mouthY + 1.5f); c(43f, noseY + 2f, 49f, noseY + 1f, 50f, noseY + 2.4f); c(51f, noseY + 1f, 57f, noseY + 2f, 57f, mouthY + 1.5f)
                q(53f, mouthY - 1f, 50f, mouthY - .5f); q(47f, mouthY - 1f, 43f, mouthY + 1.5f); z()
            }, c)
        }
    }

    private fun DrawScope.hat(h: Hat, g: Geo) {
        val felt = Color(0xFF1A1816)
        val band = Color(0xFF4A4642)
        val t = g.top
        when (h) {
            Hat.NONE -> Unit
            Hat.FEDORA -> {
                drawPath(path { m(18f, t + 11f); q(50f, t + 2f, 82f, t + 11f); q(50f, t + 17f, 18f, t + 11f); z() }, felt)
                drawPath(path { m(33f, t + 10f); c(31f, t - 12f, 44f, t - 13f, 50f, t - 8f); c(56f, t - 13f, 69f, t - 12f, 67f, t + 10f); z() }, felt)
                drawPath(path { m(33f, t + 4f); q(50f, t + 1f, 67f, t + 4f); l(67f, t + 9f); q(50f, t + 6f, 33f, t + 9f); z() }, band)
                drawPath(path { m(38f, t - 6f); q(41f, t - 9f, 45f, t - 9f) }, Color.White.copy(alpha = .15f), style = Stroke(.8f))
            }
            Hat.BOWLER -> {
                drawPath(path { m(26f, t + 10f); q(50f, t + 4f, 74f, t + 10f); q(50f, t + 14f, 26f, t + 10f); z() }, felt)
                drawArc(felt, 180f, 180f, true, Offset(33f, t - 12f), Size(34f, 40f))
                drawRect(band, Offset(33f, t + 5f), Size(34f, 3f))
                drawArc(Color.White.copy(alpha = .14f), 200f, 60f, false, Offset(37f, t - 8f), Size(26f, 30f), style = Stroke(.9f))
            }
            Hat.TOP -> {
                drawPath(path { m(25f, t + 9f); q(50f, t + 3f, 75f, t + 9f); q(50f, t + 13f, 25f, t + 9f); z() }, felt)
                drawRect(felt, Offset(35f, t - 22f), Size(30f, 30f))
                drawOval(Color(0xFF26231F), Offset(35f, t - 24f), Size(30f, 5f))
                drawRect(band, Offset(35f, t + 1f), Size(30f, 4f))
                drawLine(Color.White.copy(alpha = .14f), Offset(38f, t - 20f), Offset(38f, t + 4f), 1.2f)
            }
            Hat.CAP -> {
                drawArc(Color(0xFF2C2925), 180f, 180f, true, Offset(32f, t - 5f), Size(36f, 28f))
                drawPath(path { m(33f, t + 9f); q(22f, t + 9f, 18f, t + 13f); q(28f, t + 14f, 40f, t + 11f); z() }, Color(0xFF221F1C))
                drawCircle(Color(0xFF5A5650), 1.2f, Offset(50f, t - 4.5f))
                drawLine(Color.White.copy(alpha = .1f), Offset(50f, t - 4f), Offset(50f, t + 8f), .5f)
            }
            Hat.FLAT -> {
                drawPath(path { m(31f, t + 11f); c(29f, t - 6f, 62f, t - 8f, 71f, t + 7f); l(69f, t + 11f); z() }, Color(0xFF3A3632))
                drawPath(path { m(31f, t + 10f); q(25f, t + 11f, 24f, t + 14f); q(33f, t + 14f, 42f, t + 11f); z() }, Color(0xFF2A2724))
                for (i in 0..5) drawLine(Color.White.copy(alpha = .06f), Offset(35f + i * 6, t - 1f), Offset(33f + i * 6, t + 10f), .7f)
            }
            Hat.VISOR -> {
                drawRect(Color(0xFF2B2824), Offset(33f, t + 7f), Size(34f, 3.5f))
                drawPath(path { m(30f, t + 10f); l(70f, t + 10f); l(64f, t + 19f); l(36f, t + 19f); z() }, Color(0xFF3E4A3F).copy(alpha = .8f))
            }
            Hat.CHEF -> {
                drawRect(Color(0xFFE9E5DD), Offset(35f, t - 2f), Size(30f, 10f))
                drawCircle(Color(0xFFF1EEE8), 10f, Offset(41f, t - 8f)); drawCircle(Color(0xFFF1EEE8), 11f, Offset(52f, t - 11f)); drawCircle(Color(0xFFE2DED6), 9f, Offset(61f, t - 6f))
                drawLine(Color(0xFFBDB8AE), Offset(46f, t - 8f), Offset(47f, t + 2f), .6f)
            }
            Hat.NURSE -> {
                drawRoundRect(Color(0xFFEAE6DE), Offset(36f, t - 4f), Size(28f, 11f), CornerRadius(3f))
                drawRect(Color(0xFF3B3833), Offset(48.5f, t - 2f), Size(3f, 7f)); drawRect(Color(0xFF3B3833), Offset(46.5f, t), Size(7f, 3f))
            }
            Hat.CONDUCTOR -> {
                drawPath(path { m(32f, t + 8f); l(31f, t - 6f); q(50f, t - 10f, 69f, t - 6f); l(68f, t + 8f); z() }, Color(0xFF26231F))
                drawRect(Color(0xFF1A1816), Offset(32f, t + 5f), Size(36f, 3f))
                drawPath(path { m(32f, t + 8f); q(50f, t + 15f, 66f, t + 8f); z() }, Color(0xFF151311))
                drawRect(Color(0xFFB8AF9C), Offset(46f, t - 3f), Size(8f, 5f))
            }
        }
    }

    private fun DrawScope.mark(m: Mark, g: Geo, skin: Color) {
        val dark = Color(0xFF1C1A17)
        val eyeY = g.top + (g.chin - g.top) * .44f
        val mouthY = g.top + (g.chin - g.top) * .81f
        when (m) {
            Mark.NONE -> Unit
            Mark.BOX -> {
                drawRect(Color(0xFF8C8576), Offset(56f, 94f), Size(30f, 24f))
                drawRect(Color(0xFF6F695D), Offset(56f, 94f), Size(30f, 5f))
                drawLine(Color(0xFFD8D2C6), Offset(71f, 94f), Offset(71f, 118f), 1.2f)
                drawRect(Color(0xFFD8D2C6), Offset(60f, 104f), Size(8f, 5f))
            }
            Mark.CUP -> {
                drawRect(Color(0xFFE6E1D8), Offset(64f, 98f), Size(12f, 15f))
                drawRect(Color(0xFF6B655B), Offset(64f, 103f), Size(12f, 5f))
                drawPath(path { m(70f, 96f); q(66f, 90f, 71f, 84f) }, Color.White.copy(alpha = .35f), style = Stroke(.8f))
            }
            Mark.TIE -> {
                drawPath(path { m(47.5f, 91f); l(52.5f, 91f); l(54.5f, 112f); l(50f, 117f); l(45.5f, 112f); z() }, Color(0xFF45413C))
                for (i in 0..3) drawLine(Color.White.copy(alpha = .15f), Offset(46f, 97f + i * 5), Offset(54f, 93f + i * 5), .7f)
            }
            Mark.BOW -> {
                drawPath(path { m(50f, 91f); l(41f, 87f); l(41f, 95f); z() }, dark)
                drawPath(path { m(50f, 91f); l(59f, 87f); l(59f, 95f); z() }, dark)
                drawCircle(Color(0xFF2E2B27), 2f, Offset(50f, 91f))
            }
            Mark.GLASSES -> {
                drawRoundRect(dark, Offset(38f, eyeY - 3.5f), Size(10f, 7f), CornerRadius(1.5f), style = Stroke(1.2f))
                drawRoundRect(dark, Offset(52f, eyeY - 3.5f), Size(10f, 7f), CornerRadius(1.5f), style = Stroke(1.2f))
                drawLine(dark, Offset(48f, eyeY - 1.5f), Offset(52f, eyeY - 1.5f), 1f)
                drawLine(dark, Offset(38f, eyeY - 2f), Offset(50f - g.cw, eyeY - 3f), .9f)
                drawLine(Color.White.copy(alpha = .6f), Offset(39.5f, eyeY - 2.5f), Offset(42f, eyeY - 2.5f), .7f)
            }
            Mark.MONOCLE -> {
                drawCircle(Color.White.copy(alpha = .15f), 4.6f, Offset(57f, eyeY))
                drawCircle(Color(0xFFCFC8B8), 4.6f, Offset(57f, eyeY), style = Stroke(.9f))
                drawPath(path { m(61f, eyeY + 2f); q(64f, eyeY + 18f, 60f, 92f) }, Color(0xFFCFC8B8), style = Stroke(.4f))
            }
            Mark.MOUSTACHE -> drawPath(path {
                m(43f, mouthY - 1f); c(45f, mouthY - 5f, 49f, mouthY - 4f, 50f, mouthY - 2.5f); c(51f, mouthY - 4f, 55f, mouthY - 5f, 57f, mouthY - 1f)
                c(54f, mouthY - 2f, 51f, mouthY - 1.5f, 50f, mouthY - 1.5f); c(49f, mouthY - 1.5f, 46f, mouthY - 2f, 43f, mouthY - 1f); z()
            }, dark)
            Mark.MIRROR -> {
                drawLine(dark, Offset(50f - g.cw, g.top + 9f), Offset(50f + g.cw, g.top + 9f), 1.4f)
                drawCircle(Color(0xFFE2DED6), 5.5f, Offset(46f, g.top + 8f))
                drawCircle(Color(0xFF9F9A91), 1.8f, Offset(46f, g.top + 8f))
                drawCircle(Color.White.copy(alpha = .7f), 1.2f, Offset(44f, g.top + 6.5f))
            }
            Mark.HEADSET -> {
                drawArc(dark, 180f, 180f, false, Offset(50f - g.cw - 2f, g.top - 3f), Size(g.cw * 2 + 4f, 36f), style = Stroke(2f))
                drawRoundRect(dark, Offset(50f - g.cw - 4f, g.cheekY - 6f), Size(6f, 10f), CornerRadius(2f))
                drawPath(path { m(50f - g.cw - 1f, g.cheekY + 3f); q(40f, mouthY + 3f, 45f, mouthY + 1f) }, dark, style = Stroke(1.1f))
                drawCircle(dark, 1.6f, Offset(45f, mouthY + 1f))
            }
            Mark.SCAR -> {
                drawLine(lerp(skin, Color.White, .4f), Offset(37f, eyeY + 2f), Offset(42f, eyeY + 11f), 1f)
                for (i in 0..2) drawLine(lerp(skin, INK, .5f), Offset(37.5f + i * 1.6f, eyeY + 4f + i * 3), Offset(41.5f + i * 1.6f, eyeY + 3f + i * 3), .5f)
            }
            Mark.CIGAR -> {
                drawLine(Color(0xFF8C7B66), Offset(53f, mouthY + .5f), Offset(65f, mouthY + 2.8f), 2.6f, StrokeCap.Round)
                drawLine(Color(0xFFB9A890), Offset(54f, mouthY), Offset(64f, mouthY + 2f), .6f)
                drawCircle(Color(0xFFF2E6D0), 1.3f, Offset(65.5f, mouthY + 2.9f))
                drawPath(path { m(65f, mouthY + 1.5f); c(70f, mouthY - 8f, 62f, mouthY - 14f, 70f, mouthY - 24f) }, Color.White.copy(alpha = .3f), style = Stroke(1.1f))
            }
            Mark.PEARLS -> for (i in 0..8) {
                val x = 40f + i * 2.5f
                val y = 87f + (4f - abs(i - 4f)) * 1.1f
                drawCircle(Color(0xFFE9E6DF), 1.2f, Offset(x, y))
            }
        }
    }

    private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

    /** The silhouette only: the empty frame for «person unknown». */
    fun DrawScope.unknown() {
        val k = minOf(size.width / 100f, size.height / 120f)
        val ox = (size.width - 100 * k) / 2; val oy = (size.height - 120 * k) / 2
        withTransform({ translate(ox, oy); scale(k, k, Offset.Zero) }) {
            drawRect(Brush.radialGradient(listOf(Color(0xFFB5B0A7), Color(0xFF7F7A72)), Offset(30f, 35f), 100f), Offset.Zero, Size(100f, 120f))
            for (i in 0..12) drawLine(CHART.copy(alpha = if (i % 2 == 0) .45f else .2f), Offset(0f, 8f + i * 9f), Offset(if (i % 2 == 0) 100f else 10f, 8f + i * 9f), .5f)
            drawPath(path { m(10f, 122f); c(10f, 98f, 22f, 90f, 40f, 88f); l(60f, 88f); c(78f, 90f, 90f, 98f, 90f, 122f); z() }, Color(0xFF22201D))
            drawRect(Color(0xFF22201D), Offset(43f, 70f), Size(14f, 20f))
            drawPath(head(geo(Shape.OVAL)), Color(0xFF22201D))
            drawPath(path { m(18f, 40f); q(50f, 31f, 82f, 40f); q(50f, 46f, 18f, 40f); z() }, Color(0xFF22201D))
            drawPath(path { m(33f, 39f); c(31f, 17f, 44f, 16f, 50f, 21f); c(56f, 16f, 69f, 17f, 67f, 39f); z() }, Color(0xFF22201D))
        }
        drawRect(Grain.brush, Offset(ox, oy), Size(100 * k, 120 * k))
    }
}
