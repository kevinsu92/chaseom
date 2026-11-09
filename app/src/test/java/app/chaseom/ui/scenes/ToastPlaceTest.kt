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
}
