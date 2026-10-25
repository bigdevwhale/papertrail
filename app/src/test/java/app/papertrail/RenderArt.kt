package app.papertrail

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.papertrail.domain.Cast
import app.papertrail.domain.Suspect
import app.papertrail.ui.art.Mugshot.mugshot
import app.papertrail.ui.art.Mugshot.unknown
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the suspects' photos to PNGs (run with `-Prender`): a look at the faces without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }

    private fun render(name: String, w: Int, h: Int, draw: DrawScope.() -> Unit) {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), draw)
        File(out, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The launcher icon, flattened to docs/logo.png for the README and the site. */
    @Test fun logo() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val n = 432
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val clip = android.graphics.Path().apply { addRoundRect(0f, 0f, n.toFloat(), n.toFloat(), n * .22f, n * .22f, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!
            // The adaptive safe zone: 108 dp drawn, the middle 72 shown.
            val pad = (n * 18 / 72)
            d.setBounds(-pad, -pad, n + pad, n + pad)
            d.draw(c)
        }
        File("../docs").mkdirs()
        File("../docs/logo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun faces() {
        val cw = 300f; val ch = 360f
        val all = Cast.ALL.map { Suspect(preset = it.key) } + (1..6).map { Suspect(face = it * 7919 + 13) } + listOf(null)
        val cols = 6
        val rows = (all.size + cols - 1) / cols
        render("faces", (cw * cols).toInt(), (ch * rows).toInt()) {
            drawRect(Color(0xFF151413))
            for ((i, s) in all.withIndex()) translate((i % cols) * cw + 8, (i / cols) * ch + 8) {
                val box = Size(cw - 16, ch - 16)
                CanvasDrawScope().let { }
                drawContext.canvas.save()
                drawContext.canvas.clipRect(0f, 0f, box.width, box.height)
                val sc = this
                with(sc) {
                    val prev = drawContext.size
                    drawContext.size = box
                    if (s == null) unknown() else mugshot(s)
                    drawContext.size = prev
                }
                drawContext.canvas.restore()
            }
        }
    }
}
