package app.chaseom.stay

import app.chaseom.cabin.CabinState

/**
 * UI model of a stay. The engine only produces structured values; every sentence lives in
 * res/values/strings_stay.xml and is checked by WordingRulesTest.
 */

/** Full = EV (energy budget, toast, exchange). Reduced = everything else (notices, air, receipt). */
enum class AppMode { FULL, REDUCED }

/**
 * What each mode is allowed to show (reduced mode has no energy budget, exchange,
 * dial toast or "keep the climate on" nuance). Scenes ask the policy instead of checking the mode,
 * so a new mode (e.g. PHEV later) is one more policy object.
 */
sealed class ModePolicy(val mode: AppMode) {
    abstract val energyBudget: Boolean
    abstract val dialToast: Boolean
    abstract val exchangeCard: Boolean
    abstract val airCard: Boolean
    abstract val receiptEnergyRows: Boolean
    abstract val idlingNotice: Boolean

    data object Full : ModePolicy(AppMode.FULL) {
        override val energyBudget = true
        override val dialToast = true
        override val exchangeCard = true
        override val airCard = true
        override val receiptEnergyRows = true
        override val idlingNotice = false
    }

    data object Reduced : ModePolicy(AppMode.REDUCED) {
        override val energyBudget = false
        override val dialToast = false
        override val exchangeCard = false
        override val airCard = true // FLOWS R: same rule as EV
        override val receiptEnergyRows = false
        override val idlingNotice = true
    }

    companion object {
        fun of(mode: AppMode) = if (mode == AppMode.FULL) Full else Reduced
    }
}

/**
 * Unknown or unreadable fuel type -> REDUCED. The emulator reports [10].
 */
object ModeDetector {
    /** android.car.FuelType.ELECTRIC */
    const val FUEL_ELECTRIC = 10

    fun decide(fuelTypes: List<Int>?, fuelReadFailed: Boolean, socReadable: Boolean?, capacityKwh: Float?): AppMode {
        if (fuelReadFailed || fuelTypes.isNullOrEmpty()) return AppMode.REDUCED
        val pureElectric = fuelTypes.all { it == FUEL_ELECTRIC }
        val evCapable = socReadable == true && (capacityKwh ?: 0f) > 0f
        return if (pureElectric && evCapable) AppMode.FULL else AppMode.REDUCED
    }

    fun decide(c: CabinState): AppMode = decide(c.fuelTypes, c.fuelTypeReadFailed, c.socReadable, c.batteryCapacityKwh)
}

/** N1 hour, N2 night, N3 backrest. Higher [priority] wins when they overlap. */
enum class NoticeKind(val priority: Int) { HOUR(1), NIGHT(2), BACKREST(3) }

/** N0 start banner: expanded for 10 s, then a permanent thin warning line. */
data class Banner(val expanded: Boolean)

data class Hero(
    /** Uncapped estimate; null when it cannot be computed. */
    val minutes: Int?,
    val overCap: Boolean,
    val hvacOff: Boolean,
    /** Estimated draw at the current setting (kW). */
    val drawKw: Float = 0f,
    /** SOC at or under tomorrow's share: the hero shows a status line, never "약 0분". */
    val belowKeep: Boolean = false,
    /** Below keep: estimated km of tomorrow's distance the battery still covers. */
    val coveredKm: Int? = null,
)

data class BarModel(val socPercent: Int, val keepPercent: Float, val keepKm: Int, val label: String, val belowKeep: Boolean)

enum class KeepBanner { NEAR, BELOW }

/** One changed climate item, shown as a read-only label in the dial toast. */
sealed class ClimateChange {
    data class Power(val on: Boolean) : ClimateChange()
    data class Temp(val celsius: Float) : ClimateChange()
    data class Fan(val level: Int) : ClimateChange()
    data class Ac(val on: Boolean) : ClimateChange()
    data class Recirc(val on: Boolean) : ClimateChange()
    data class SeatHeat(val level: Int) : ClimateChange()
}

data class Toast(
    /** Last two changed items of the batch, oldest first. */
    val items: List<ClimateChange>,
    /** Signed minutes between the 3-hour-capped before/after values; |delta| < 1 = "no real change". */
    val deltaMinutes: Int,
    val untilMs: Long,
    /** Both before and after are at/above the display cap: show "3시간 이상 유지 (추정)" instead of a delta. */
    val heldAboveCap: Boolean = false,
    /**
     * False when the batch switched between recirculation and outside air: the toast then names the
     * change only and shows no minutes, in either direction. The hero still shows the new time at once.
     */
    val showMinutes: Boolean = true,
    /**
     * Only when [heldAboveCap]: the signed change of the stay time as a percentage (same battery, power
     * before / after - 1). The minutes above the cap are never shown. null when |change| < 1 %.
     */
    val capRatePercent: Int? = null,
)

enum class ExchangeRule { X1_SEAT_HEAT, X2_FAN, X3_AC }

data class Exchange(val rule: ExchangeRule, val gainMinutes: Int)

/** Receipt rows in display order. [dot] = warning dot (open / on). */
sealed class ReceiptRow(val dot: Boolean) {
    data class Stayed(val minutes: Int) : ReceiptRow(false)
    /** [kind]: VALUE = "약 n kWh", SMALL = under 1 kWh, CHARGED = SOC rose during the stay. */
    data class Energy(val kwh: String, val kind: EnergyKind = EnergyKind.VALUE) : ReceiptRow(false)
    /** [coveredKm] null = whole distance still covered; otherwise the estimate below [km]. */
    data class Keep(val km: Int, val coveredKm: Int?) : ReceiptRow(coveredKm != null)
    /** [known] false = the car did not report every window: "확인 안 됨", never "닫힘". */
    data class Windows(val open: List<String>, val known: Boolean = true) : ReceiptRow(open.isNotEmpty())
    data class Light(val on: Boolean?) : ReceiptRow(on == true)
    data class ChargePort(val open: Boolean?) : ReceiptRow(open == true)
    /** Always grey, never a dot, never green. */
    data class Rear(val detected: Boolean) : ReceiptRow(false)
}

/**
 * VALUE = "약 n kWh", SMALL = under 1 kWh, CHARGED = SOC rose, IMPLAUSIBLE = the SOC drop is faster than
 * any parked climate load could cause (e.g. a Virtual Console jump): "확인 안 됨", never a made-up number.
 */
enum class EnergyKind { VALUE, SMALL, CHARGED, IMPLAUSIBLE }

data class Receipt(val rows: List<ReceiptRow>, val createdAtMs: Long) {
    /** "뒷좌석 직접 확인" is part of every receipt, rendered as the last line. */
    val rearCheckAlways: Boolean get() = true
}

data class StaySettings(
    val keepKm: Int = DEFAULT_KM,
    val label: String = DEFAULT_LABEL,
    /** false until the user saves settings once: the keep line then says "기본값". */
    val saved: Boolean = true,
) {
    companion object {
        const val DEFAULT_KM = 30
        const val MIN_KM = 5
        const val MAX_KM = 300
        const val STEP_KM = 5
        val PRESETS = listOf(10, 20, 30, 50, 80)
        /** Keys of the label chips (strings s_label_*). */
        val LABELS = listOf("work", "school", "home", "pickup", "etc")
        const val DEFAULT_LABEL = "work"
    }
}

data class StayUi(
    val parked: Boolean = false,
    val policy: ModePolicy = ModePolicy.Reduced,
    val elapsedMinutes: Int = 0,
    val banner: Banner? = null,
    val notice: NoticeKind? = null,
    val hero: Hero? = null,
    val bar: BarModel? = null,
    val keepBanner: KeepBanner? = null,
    val climate: ClimateSetting? = null,
    val toast: Toast? = null,
    val exchange: Exchange? = null,
    val seatHeatLong: Boolean = false,
    val airMinutes: Int? = null,
    val receipt: Receipt? = null,
    val settings: StaySettings = StaySettings(),
    val backrestSupported: Boolean = false,
    /** Wall time of the last climate change (K1 hourglass emphasis window). */
    val lastClimateChangeMs: Long? = null,
    /** Passenger seat heater level, island input only. */
    val passengerSeatHeat: Int? = null,
    /** No gear has been read yet since start: "차와 연결하는 중" instead of the driving glance. */
    val connecting: Boolean = false,
    /** First stay only, until the first climate change: "다이얼을 돌려 보세요" hint under the island. */
    val firstStayHint: Boolean = false,
    /** Minutes recirculation has held with the windows closed, once past 20 min (the circling cloud); else null. */
    val recircMinutes: Int? = null,
    /** Boat = car: trunk / charge port known open (unknown = closed on the picture, never guessed open). */
    /** Wiper lever on AUTO and actually wiping; manual Low/High/once never count. Picture only. */
    val rain: Boolean = false,
    val trunkOpen: Boolean = false,
    val chargePortOpen: Boolean = false,
    /** The saved stay was too old or the battery changed: a new stay started (title "새로 시작했어요"). */
    val restartedFromStale: Boolean = false,
    /** Monotonic time the car arrived in P after driving (mooring); null = draw the end picture. */
    val moorAtMs: Long? = null,
)
