package app.chaseom.ui.scenes

import app.chaseom.stay.Hero
import app.chaseom.stay.ModePolicy
import app.chaseom.stay.StayUi
import org.junit.Assert.assertEquals
import org.junit.Test

/** HANDOFF 9.6 v7: the dial toast never covers a number or text line (Small window found 11-10). */
class ToastPlaceTest {
    private fun ui(hero: Hero?) = StayUi(parked = true, policy = ModePolicy.Full, hero = hero)

    @Test fun wideGoesToTheHeroRowSmallIntoTheIslandSky() {
        val h = Hero(146, false, false)
        assertEquals(ToastPlace.HERO_ROW, toastPlace(ui(h), wide = true, islandFace = true))
        assertEquals(ToastPlace.ISLAND_TOP, toastPlace(ui(h), wide = false, islandFace = true))
    }

    @Test fun noHeroRowFallsBackToTheBottom() {
        // Climate off or below tomorrow's share: no hero number row to sit next to.
        assertEquals(ToastPlace.BOTTOM, toastPlace(ui(Hero(null, false, true)), wide = true, islandFace = true))
        assertEquals(ToastPlace.BOTTOM, toastPlace(ui(Hero(0, false, false, belowKeep = true)), wide = true, islandFace = true))
        // Small without the island face (bar face): bottom as before.
        assertEquals(ToastPlace.BOTTOM, toastPlace(ui(Hero(146, false, false)), wide = false, islandFace = false))
    }

    @Test fun islandScreenHasNoClimateStatusLine() {
        // HQ 2026-11-11 / HANDOFF 8 round1b: three text lines on the island screen; the bar face keeps it.
        assertEquals(false, climateLineShown(islandFace = true))
        assertEquals(true, climateLineShown(islandFace = false))
    }

    @Test fun upIsInkDownIsAmberNoGreen() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF131417), UP_CIRCLE)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFB020), DOWN_CIRCLE)
    }

    @Test fun safetyBannerStaysAboveEveryOverlay() {
        // QA ROUND1B_GATE §10-①: the receipt or a notice scrim never dims the N0 banner.
        val base = StayUi(parked = true, policy = ModePolicy.Full, banner = app.chaseom.stay.Banner(expanded = false))
        assertEquals(false, bannerAboveScrim(base))
        assertEquals(true, bannerAboveScrim(base.copy(receipt = app.chaseom.stay.Receipt(emptyList(), 0L))))
        app.chaseom.stay.NoticeKind.entries.forEach { assertEquals(true, bannerAboveScrim(base.copy(notice = it))) }
        assertEquals(false, bannerAboveScrim(base.copy(banner = null, notice = app.chaseom.stay.NoticeKind.HOUR)))
    }
}
