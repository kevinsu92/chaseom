package app.chaseom.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.chaseom.AppContainer
import app.chaseom.stay.AppMode
import app.chaseom.stay.Page
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosDimens

/**
 * Scene router. Each scene is a self-contained composable in this package that only reads the
 * [app.chaseom.stay.StayUi] model and reports user intents through [StayActions], so scenes
 * can be added, swapped or reordered without touching the engine (modular scene structure).
 *
 * Order of precedence:
 *  1. not PARKED -> [GlanceScene] only (no numbers, no buttons; all cards closed)
 *  2. parked pages: onboarding / settings / about
 *  3. stay scene by mode policy (H full / R reduced), then overlays: receipt, notice card on top
 */
@Composable
fun SceneHost(container: AppContainer) {
    val stay = container.stay
    val ui by stay.ui.collectAsState()
    val page by stay.page.collectAsState()
    val actions = StayActions(stay)
    Box(Modifier.fillMaxSize().background(Pleos.colors.backgroundPrimary)) {
        if (!ui.parked) {
            // Before the first gear value arrives, say "connecting" instead of "driving".
            if (ui.connecting) ConnectingScene() else GlanceScene(firstRun = stay.firstRun)
            return@Box
        }
        val content = Modifier.fillMaxSize()
            .padding(horizontal = PleosDimens.marginWide)
            .padding(top = PleosDimens.safeTop, bottom = PleosDimens.safeBottom)
        when (page) {
            Page.ONBOARDING -> OnboardingScene(ui.policy.mode, actions, content)
            Page.SETTINGS -> SettingsScene(ui.settings, actions, content)
            Page.ABOUT -> AboutScene(ui, actions, content)
            Page.NONE -> {
                if (ui.policy.mode == AppMode.FULL) StayFullScene(ui, actions, content) else StayReducedScene(ui, actions, content)
                ui.receipt?.let { ReceiptOverlay(it, ui.policy, actions, ui) }
                ui.notice?.let { NoticeOverlay(it, ui.policy, actions) }
                // The N0 safety banner stays at full contrast above any scrim.
                if (bannerAboveScrim(ui)) androidx.compose.foundation.layout.Column(content) { N0Banner(ui, actions) }
            }
        }
    }
}

/** User intents a scene may send. Thin wrapper so scenes do not depend on the controller type. */
class StayActions(private val c: app.chaseom.stay.StayController) {
    fun closeBanner() = c.closeBanner()
    fun acknowledgeNotice() = c.acknowledgeNotice()
    fun dismissExchange(rule: app.chaseom.stay.ExchangeRule) = c.dismissExchange(rule)
    fun dismissAir() = c.dismissAir()
    fun closeReceipt() = c.closeReceipt()
    fun plantWaitFlag(minutes: Int) = c.plantWaitFlag(minutes)
    fun removeWaitFlag() = c.removeWaitFlag()
    fun finishOnboarding(toSettings: Boolean) = c.finishOnboarding(toSettings)
    fun open(page: Page) = c.open(page)
    fun saveSettings(km: Int, label: String) = c.saveSettings(km, label)
    fun useDefaults() = c.useDefaults()
}
