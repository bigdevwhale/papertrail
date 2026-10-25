package app.papertrail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.papertrail.data.DemoSeed
import app.papertrail.ui.Home
import app.papertrail.ui.Nav
import app.papertrail.ui.Words
import app.papertrail.ui.board.BoardScreen
import app.papertrail.ui.board.CaseSheet
import app.papertrail.ui.components.LocalToast
import app.papertrail.ui.components.Toaster
import app.papertrail.ui.components.noir
import app.papertrail.ui.files.DossierSheet
import app.papertrail.ui.files.FilesScreen
import app.papertrail.ui.office.EntrySheet
import app.papertrail.ui.office.EnvelopesSheet
import app.papertrail.ui.office.OfficeScreen
import app.papertrail.ui.onboarding.Onboarding
import app.papertrail.ui.paper.PaperScreen
import app.papertrail.ui.safe.SafeScreen
import app.papertrail.ui.settings.SettingsSheet
import app.papertrail.ui.theme.TrailTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** Every main screen over the demo history, drawn to PNGs (run with `-Prender`), English and Russian. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }
    private val nav = Nav({}, {}, {})

    private fun home(): Home = runBlocking {
        Locale.setDefault(Locale.forLanguageTag(lang))
        val app = ApplicationProvider.getApplicationContext<PaperTrailApp>()
        val c = app.container
        DemoSeed.seedIfEmpty(c)
        val s = c.repo.refresh()
        val st = c.settings.now()
        Home(s, st, Words(lang == "ru", st.gender, st.currency, st.currencyFirst), System.currentTimeMillis())
    }

    private fun shot(name: String, content: @Composable (Home) -> Unit) {
        val h = home()
        rule.mainClock.autoAdvance = false
        rule.setContent { TrailTheme { CompositionLocalProvider(LocalToast provides Toaster()) { Box(Modifier.fillMaxSize().noir()) { content(h) } } } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun welcome() = shot("0-welcome") { Onboarding(it.w, it.settings.copy(onboarded = false)) }
    @Test fun office() = shot("1-office") { OfficeScreen(it, nav) }
    @Test fun envelopes() = shot("2-envelopes") { EnvelopesSheet(it) {} }
    @Test fun board() = shot("3-board") { BoardScreen(it, nav) }
    @Test fun case() = shot("4-case") { h -> CaseSheet(h, (h.s.openCases.firstOrNull() ?: h.s.cases.last()).id) {} }
    @Test fun files() = shot("5-files") { FilesScreen(it, nav) }
    @Test fun dossier() = shot("6-dossier") { h -> DossierSheet(h, h.s.suspects.first { it.preset == "courier" }.id, nav) {} }
    @Test fun safe() = shot("7-safe") { SafeScreen(it) }
    @Test fun paper() = shot("8-paper") { PaperScreen(it) }
    @Test fun entry() = shot("9-entry") { EntrySheet(it, 0, false) {} }
    @Test fun settings() = shot("10-settings") { SettingsSheet(it) {} }
}

class RenderArtScreensEn : ScreensBase() {
    override val lang = "en"
}

@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() {
    override val lang = "ru"
}
