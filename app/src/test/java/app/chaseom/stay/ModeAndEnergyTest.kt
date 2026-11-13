package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeAndEnergyTest {

    // Full mode only for an EV whose SOC and battery capacity can be read
    @Test fun modeRules() {
        assertEquals(AppMode.FULL, ModeDetector.decide(listOf(10), false, true, 77.4f))
        assertEquals(AppMode.REDUCED, ModeDetector.decide(null, true, true, 77.4f)) // read failed
        assertEquals(AppMode.REDUCED, ModeDetector.decide(null, false, true, 77.4f)) // unknown
        assertEquals(AppMode.REDUCED, ModeDetector.decide(emptyList(), false, true, 77.4f))
        assertEquals(AppMode.REDUCED, ModeDetector.decide(listOf(1), false, null, null)) // gasoline
        assertEquals(AppMode.REDUCED, ModeDetector.decide(listOf(1, 10), false, true, 13f)) // PHEV
        assertEquals(AppMode.REDUCED, ModeDetector.decide(listOf(10), false, null, 77.4f)) // no SOC
        assertEquals(AppMode.REDUCED, ModeDetector.decide(listOf(10), false, true, null)) // no capacity
    }

    // Reduced policy has no energy features
    @Test fun reducedPolicyHasNoEnergyFeatures() {
        val p = ModePolicy.Reduced
        assertFalse(p.energyBudget); assertFalse(p.dialToast); assertFalse(p.exchangeCard); assertFalse(p.receiptEnergyRows)
        assertTrue(p.idlingNotice)
        assertTrue(ModePolicy.Full.energyBudget && !ModePolicy.Full.idlingNotice)
    }

    @Test fun keepShareIsKmOverEfficiencyTimes1point2() {
        // 30 km / 5 km/kWh * 1.2 = 7.2 kWh = 9.30 % of 77.4 kWh
        assertEquals(9.30f, EnergyModel.keepPercent(30, 77.4f, 5f), 0.01f)
    }

    @Test fun efficiencyUsesTheSmallerOfVehicleAndDefault() {
        assertEquals(5f, EnergyModel.efficiency(null), 0f)
        assertEquals(4.2f, EnergyModel.efficiency(4.2f), 0f)
        assertEquals(5f, EnergyModel.efficiency(7.5f), 0f)
        assertEquals(5f, EnergyModel.efficiency(250f), 0f) // implausible unit -> ignored
    }

    @Test fun moreHeatMeansMoreDrawAndFewerMinutes() {
        val low = ClimateSetting(true, 22f, 3, false, false, 0)
        val high = low.copy(setTempC = 26f, fanSpeed = 6)
        assertTrue(EnergyModel.drawKw(high) > EnergyModel.drawKw(low))
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        assertTrue(EnergyModel.minutesToKeep(62, keep, 77.4f, EnergyModel.drawKw(high)) <
            EnergyModel.minutesToKeep(62, keep, 77.4f, EnergyModel.drawKw(low)))
    }

    @Test fun climateOffStillCountsBaseLoad() =
        assertEquals(EnergyModel.BASE_KW, EnergyModel.drawKw(ClimateSetting(false, 26f, 6, true, false, 0)), 0.0001f)

    @Test fun usedKwhNeverNegative() {
        assertEquals("2.3", EnergyModel.usedKwh(62, 59, 77.4f))
        assertEquals("0.0", EnergyModel.usedKwh(59, 62, 77.4f))
    }

    private val keep = EnergyModel.keepPercent(30, 77.4f, 5f)

    @Test fun exchangeRulesAndMinimumGain() {
        assertNull(EnergyModel.exchange(62, 77.4f, keep, ClimateSetting(false, 26f, 3, false, false, 0), emptySet()))
        assertNull(EnergyModel.exchange(62, 77.4f, keep, ClimateSetting(true, 21f, 3, false, false, 2), emptySet()))
        assertNull(EnergyModel.exchange(62, 77.4f, keep, ClimateSetting(true, 26f, 3, false, false, 0), emptySet())) // above the 3 h cap
        // Sourced coefficients (ENERGY_MODEL 3.2): -5 C set point + seat heat 2 saves ~0.29 kW, so the
        // gain clears MIN_GAIN only with enough battery (demo SOC 20 %); at 14 % it is ~8 min.
        assertNull(EnergyModel.exchange(14, 77.4f, keep, ClimateSetting(true, 26f, 3, false, false, 0), emptySet()))
        val x1 = EnergyModel.exchange(20, 77.4f, keep, ClimateSetting(true, 26f, 3, false, false, 0), emptySet())!!
        assertTrue(x1.gainMinutes <= EnergyModel.DISPLAY_CAP_MINUTES)
        assertEquals(ExchangeRule.X1_SEAT_HEAT, x1.rule)
        assertTrue(x1.gainMinutes >= EnergyModel.MIN_GAIN_MINUTES)
    }

    @Test fun dismissedRuleIsNotOfferedAgain() {
        val s = ClimateSetting(true, 26f, 6, false, false, 0)
        val first = EnergyModel.exchange(20, 77.4f, keep, s, emptySet())!!
        val second = EnergyModel.exchange(20, 77.4f, keep, s, setOf(first.rule))
        assertTrue(second?.rule != first.rule)
    }

    @Test fun coveredKm() = assertEquals(22, EnergyModel.coveredKm(7, 77.4f, 5f))

    // The coefficients are the sourced table (docs/ENERGY_MODEL.md 3.2).
    @Test fun coefficientsMatchSourcedTable() {
        assertEquals(0.30f, EnergyModel.BASE_KW, 0f)
        assertEquals(2.3f, EnergyModel.drawKw(ClimateSetting(true, 22f, 0, false, false, 0)) - EnergyModel.BASE_KW, 0.0001f)
        assertEquals(2.3f * 36f / 32f, EnergyModel.drawKw(ClimateSetting(true, 26f, 0, false, false, 0)) - EnergyModel.BASE_KW, 0.0001f)
        // A/C coefficient on its own: a summer reading (no heating demand). H2: with heating demand see AcHeatTest.
        assertEquals(2.0f, EnergyModel.drawKw(ClimateSetting(true, 26f, 0, true, false, 0, outsideC = 30f)) - EnergyModel.BASE_KW, 0.0001f)
        assertEquals(2.3f * 0.76f, EnergyModel.drawKw(ClimateSetting(true, 22f, 0, false, true, 0)) - EnergyModel.BASE_KW, 0.0001f)
        assertEquals(0.03f * 3, EnergyModel.drawKw(ClimateSetting(true, 22f, 3, false, false, 0)) - EnergyModel.BASE_KW - 2.3f, 0.0001f)
        assertEquals(listOf(0f, 0.049f, 0.066f, 0.100f), (0..3).map { EnergyModel.seatKw(it) })
        assertEquals(5f, EnergyModel.DEFAULT_KM_PER_KWH, 0f)
        assertEquals(10, EnergyModel.MIN_GAIN_MINUTES)
        assertEquals(180, EnergyModel.DISPLAY_CAP_MINUTES)
    }
}
