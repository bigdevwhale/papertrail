package app.papertrail.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The office's sounds, synthesised once into small WAV files: typewriter keys and the carriage bell,
 * a rubber stamp, an envelope, a match struck, a pushpin, the safe's dial, and rain on the window.
 */
class Sfx(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = IntArray(Snd.entries.size)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var rainStream = 0
    @Volatile var on = true
        set(v) { field = v; if (!v) rain(false) }

    fun load() {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        for (s in Snd.entries) {
            val f = File(dir, "${s.name.lowercase()}1.wav")
            if (!f.exists()) Wav.write(f, s.make())
            ids[s.ordinal] = pool.load(f.absolutePath, 1)
        }
    }

    fun play(s: Snd, vol: Float = 1f, rate: Float = 1f) {
        if (on && ids[s.ordinal] != 0) pool.play(ids[s.ordinal], vol, vol, 1, 0, rate)
    }

    /** Rain on the office window, looped quietly while the office is on screen. */
    fun rain(play: Boolean) {
        if (play && on && rainStream == 0 && ids[Snd.RAIN.ordinal] != 0) rainStream = pool.play(ids[Snd.RAIN.ordinal], .16f, .16f, 0, -1, 1f)
        if (!play && rainStream != 0) { pool.stop(rainStream); rainStream = 0 }
    }

    fun feel(ms: Long, amp: Int = 80) {
        runCatching { if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255))) }
    }
}

enum class Snd(val make: () -> ShortArray) {
    KEY(Synth::key), BELL(Synth::bell), STAMP(Synth::stamp), ENVELOPE(Synth::envelope), MATCH(Synth::match), PIN(Synth::pin), DIAL(Synth::dial), RAIN(Synth::rain)
}

object Synth {
    const val RATE = 22050

    private fun pcm(seconds: Double, f: (t: Double, i: Int) -> Double): ShortArray {
        val n = (RATE * seconds).toInt()
        return ShortArray(n) { i -> (f(i / RATE.toDouble(), i) * 32000).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /** A typewriter key: the type bar's snap on the platen and a low knock of the frame. */
    fun key(): ShortArray {
        val r = Random(5)
        var lp = 0.0
        return pcm(.07) { t, _ ->
            val x = r.nextDouble() * 2 - 1
            lp += (x - lp) * .35
            val snap = (x - lp) * exp(-t / .006) * .8
            val knock = sin(2 * PI * 180 * t) * exp(-t * 60) * .45
            snap + knock
        }
    }

    /** The carriage bell at the end of a line. */
    fun bell(): ShortArray = pcm(1.1) { t, _ ->
        (sin(2 * PI * 2093 * t) + .45 * sin(2 * PI * 5270 * t) + .2 * sin(2 * PI * 7600 * t)) * exp(-t * 4.5) * .3
    }

    /** A rubber stamp coming down on paper: a soft thump with a papery slap. */
    fun stamp(): ShortArray {
        val r = Random(9)
        return pcm(.3) { t, _ ->
            val body = sin(2 * PI * (110 - 60 * t) * t) * exp(-t * 18) * .8
            val slap = (r.nextDouble() * 2 - 1) * exp(-t * 70) * .4
            body + slap
        }
    }

    /** An envelope slid across the desk. */
    fun envelope(): ShortArray {
        val r = Random(11)
        var hp = 0.0
        var prev = 0.0
        return pcm(.4) { t, _ ->
            val x = r.nextDouble() * 2 - 1
            hp = .8 * (hp + x - prev); prev = x
            hp * sin(PI * t / .4).pow(1.5) * .3
        }
    }

    /** A match struck: a scratch, then the flare's hiss. */
    fun match(): ShortArray {
        val r = Random(13)
        var lp = 0.0
        return pcm(.9) { t, _ ->
            val x = r.nextDouble() * 2 - 1
            lp += (x - lp) * .25
            val scratch = if (t < .12) x * sin(PI * t / .12) * .5 else 0.0
            val flare = if (t > .1) lp * exp(-(t - .1) * 3) * (1 - exp(-(t - .1) * 40)) * .6 else 0.0
            scratch + flare
        }
    }

    /** A pushpin into cork. */
    fun pin(): ShortArray {
        val r = Random(17)
        return pcm(.06) { t, _ -> ((r.nextDouble() * 2 - 1) * .4 + sin(2 * PI * 900 * t) * .4) * exp(-t * 90) }
    }

    /** One notch of the safe's dial. */
    fun dial(): ShortArray = pcm(.05) { t, _ -> (sin(2 * PI * 3200 * t) * .5 + sin(2 * PI * 1400 * t) * .3) * exp(-t * 140) }

    /** Rain on glass: soft filtered noise and scattered drops; it loops, so it fades at neither end. */
    fun rain(): ShortArray {
        val r = Random(19)
        var lp = 0.0
        var lp2 = 0.0
        val drops = List(90) { r.nextDouble() * 4.0 to (900 + r.nextDouble() * 2600) }
        return pcm(4.0) { t, _ ->
            val x = r.nextDouble() * 2 - 1
            lp += (x - lp) * .12; lp2 += (lp - lp2) * .3
            var d = 0.0
            for ((at, f) in drops) { val dt = t - at; if (dt in 0.0..0.03) d += sin(2 * PI * f * dt) * exp(-dt * 160) * .25 }
            lp2 * .9 + d
        }
    }
}

object Wav {
    fun write(f: File, pcm: ShortArray) {
        val data = ByteArrayOutputStream()
        for (s in pcm) { data.write(s.toInt() and 0xFF); data.write((s.toInt() shr 8) and 0xFF) }
        val bytes = data.toByteArray()
        DataOutputStream(f.outputStream().buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF); o.write((v shr 16) and 0xFF); o.write((v shr 24) and 0xFF) }
            fun le16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
            o.writeBytes("RIFF"); le32(36 + bytes.size); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(Synth.RATE); le32(Synth.RATE * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(bytes.size); o.write(bytes)
        }
    }
}
