package app.papertrail.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.papertrail.R
import app.papertrail.container
import app.papertrail.data.Settings
import app.papertrail.domain.Ledger
import app.papertrail.domain.State
import app.papertrail.domain.Today
import app.papertrail.notify.Notify
import app.papertrail.ru
import app.papertrail.ui.board.BoardScreen
import app.papertrail.ui.board.CaseSheet
import app.papertrail.ui.components.LocalToast
import app.papertrail.ui.components.ToastHost
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
import app.papertrail.ui.theme.C
import app.papertrail.ui.theme.T
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Everything a screen reads, at one moment. */
class Home(val s: State, val settings: Settings, val w: Words, val now: Long, val zone: ZoneId = ZoneId.systemDefault()) {
    val ru get() = w.ru
    val today: Today = Ledger.today(settings.plan, s.clues, s.bills, now, zone)
}

enum class Tab(val label: Int) { OFFICE(R.string.tab_office), BOARD(R.string.tab_board), FILES(R.string.tab_files), SAFE(R.string.tab_safe), PAPER(R.string.tab_paper) }

sealed interface Overlay {
    /** A new clue ([id] 0) or one to edit. */
    data class Entry(val id: Long = 0, val fee: Boolean = false) : Overlay
    data object Envelopes : Overlay
    data class CaseOpen(val id: Long) : Overlay
    data class Dossier(val id: Long) : Overlay
    data object Settings : Overlay
}

/** How screens ask for an overlay. */
class Nav(val push: (Overlay) -> Unit, val pop: () -> Unit, val tab: (Tab) -> Unit)

@Composable
fun TrailRoot(settings: Settings) {
    val ctx = LocalContext.current
    val c = ctx.container
    val state by c.repo.state.collectAsStateWithLifecycle()
    val words = remember(ctx.ru, settings.gender, settings.currency, settings.currencyFirst) { Words.of(ctx, settings) }
    val toaster = remember { Toaster() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.sound) { c.sfx.on = settings.sound }
    if (!settings.onboarded) {
        CompositionLocalProvider(LocalToast provides toaster) { Box(Modifier.fillMaxSize().noir()) { Onboarding(words, settings); ToastHost(toaster) } }
        return
    }
    // Each time the office comes to the front: the detective goes over the files, the evening report is booked.
    val life = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        life.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            c.repo.investigate(c.settings.now().plan)
            Notify.plan(ctx)
            while (true) { now = System.currentTimeMillis(); delay(30_000) }
        }
    }
    val s = state ?: run { Box(Modifier.fillMaxSize().noir()); return }
    val home = Home(s, settings, words, now)

    var tab by remember { mutableStateOf(Tab.OFFICE) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    val nav = remember { Nav(push = { overlays.add(it) }, pop = { if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex) }, tab = { tab = it }) }
    BackHandler(enabled = overlays.isEmpty() && tab != Tab.OFFICE) { tab = Tab.OFFICE }
    DisposableEffect(tab, overlays.isEmpty()) {
        c.sfx.rain(tab == Tab.OFFICE && overlays.isEmpty())
        onDispose { c.sfx.rain(false) }
    }

    CompositionLocalProvider(LocalToast provides toaster) {
        Box(Modifier.fillMaxSize().noir()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        Tab.OFFICE -> OfficeScreen(home, nav)
                        Tab.BOARD -> BoardScreen(home, nav)
                        Tab.FILES -> FilesScreen(home, nav)
                        Tab.SAFE -> SafeScreen(home)
                        Tab.PAPER -> PaperScreen(home)
                    }
                }
                TabBar(tab, home) { tab = it }
            }
            when (val o = overlays.lastOrNull()) {
                is Overlay.Entry -> EntrySheet(home, o.id, o.fee, onClose = nav.pop)
                Overlay.Envelopes -> EnvelopesSheet(home, onClose = nav.pop)
                is Overlay.CaseOpen -> CaseSheet(home, o.id, onClose = nav.pop)
                is Overlay.Dossier -> DossierSheet(home, o.id, nav, onClose = nav.pop)
                Overlay.Settings -> SettingsSheet(home, onClose = nav.pop)
                null -> Unit
            }
            ToastHost(toaster)
        }
    }
}

@Composable
private fun TabBar(tab: Tab, h: Home, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.desk)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            for (t in Tab.entries) {
                val sel = t == tab
                val badge = when (t) {
                    Tab.BOARD -> h.s.openCases.size
                    Tab.OFFICE -> h.s.envelopes.size
                    else -> 0
                }
                Box(Modifier.weight(1f).clickable { onTab(t) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(t.label).uppercase(),
                        style = T.Label.copy(color = if (sel) C.paper else C.smoke, letterSpacing = androidx.compose.ui.unit.TextUnit(.3f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        maxLines = 1, softWrap = false,
                    )
                    // Something waiting there: envelopes to sort, cases to try.
                    if (badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(6.dp).background(C.red))
                    if (sel) Box(Modifier.align(Alignment.BottomCenter).offset(y = 10.dp).fillMaxWidth(.4f).height(2.dp).background(C.red))
                }
            }
        }
    }
}
