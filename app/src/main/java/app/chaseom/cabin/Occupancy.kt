package app.chaseom.cabin

/**
 * Rear-seat occupancy from two sources (build G).
 *
 * 1. AAOS `SEAT_OCCUPANCY` (android.car.VehicleSeatOccupancyState: 0 UNKNOWN, 1 VACANT, 2 OCCUPIED). Its meaning is
 *    fixed by the platform, so when it can be read it wins. Read permission is `android.car.permission.READ_CAR_SEATS`
 *    (dangerous) or `CONTROL_CAR_SEATS` (signature|privileged). The app never shows a runtime permission dialog, so
 *    this source is used only when the permission is already granted (pre-granted by the vehicle maker, or
 *    `adb shell pm grant` on the emulator).
 * 2. Pleos `SeatOccupancyListener` (Boolean). The Pleos Connect 2.1.0 emulator reports it inverted (false while
 *    the Virtual Console says "Occupied"). Before build G the app inverted it in every build, a real car included.
 *    Now it is inverted only on the emulator ([invertPleos] = [isEmulatorBuild]); on a car the SDK value is used as is.
 *
 * Null stays null: the receipt then says "확인 안 됨", never "감지 안 됨".
 */
object Occupancy {
    const val AAOS_UNKNOWN = 0
    const val AAOS_VACANT = 1
    const val AAOS_OCCUPIED = 2

    /** android.car.VehicleAreaSeat ROW_2_LEFT / ROW_2_RIGHT. */
    const val AREA_ROW_2_LEFT = 0x0010
    const val AREA_ROW_2_RIGHT = 0x0040

    fun fromAaos(state: Int?): Boolean? = when (state) {
        AAOS_OCCUPIED -> true
        AAOS_VACANT -> false
        else -> null
    }

    fun resolve(aaosState: Int?, pleosValue: Boolean?, invertPleos: Boolean): Boolean? =
        fromAaos(aaosState) ?: pleosValue?.let { if (invertPleos) !it else it }

    /**
     * True on the Android emulator (goldfish / ranchu kernel, or an emulator / sdk build fingerprint), which is what
     * Pleos Connect runs on. A real head unit has neither.
     */
    fun isEmulatorBuild(hardware: String?, fingerprint: String?, product: String?): Boolean {
        val hw = hardware.orEmpty().lowercase()
        val fp = fingerprint.orEmpty().lowercase()
        val pr = product.orEmpty().lowercase()
        return hw == "ranchu" || hw == "goldfish" ||
            fp.startsWith("generic") || fp.contains("/sdk_") || fp.contains("emulator") ||
            pr.startsWith("sdk_") || pr.contains("emulator")
    }
}
