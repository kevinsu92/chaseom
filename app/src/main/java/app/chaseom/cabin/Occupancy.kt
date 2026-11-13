package app.chaseom.cabin

/**
 * Rear-seat occupancy from the Pleos `SeatOccupancyListener` (build G).
 *
 * Why the value is inverted, with evidence (2026-11-13):
 *  - Pleos Vehicle SDK 2.3.2 builds the Boolean in `ai.pleos.playground.vehicle.callback.a` as
 *    `Integer.parseInt(raw) == 1` (javap -c of Vehicle-2.3.2-runtime.jar).
 *  - The raw value is the AAOS `SEAT_OCCUPANCY` enum (android.car.VehicleSeatOccupancyState):
 *    0 UNKNOWN, 1 VACANT, 2 OCCUPIED. With the Virtual Console seat "empty", the SDK log shows `value = 1` and the
 *    listener receives `true`.
 *  - So with this SDK, `true` means VACANT and `false` means OCCUPIED or UNKNOWN. The mapping lives in the SDK inside
 *    our APK, not in the emulator, so it applies on a real car with a standard VHAL as well. (Before build G the
 *    flag was hard-coded and explained as an emulator bug.)
 *
 * Rules:
 *  - SDK version verified above -> invert. `false` (OCCUPIED or UNKNOWN) reads as "탑승 감지됨", the side that sends
 *    the driver to look.
 *  - Any other SDK version -> null: the receipt says "확인 안 됨" until the mapping is checked again. A test pins the
 *    version in gradle/libs.versions.toml to [VERIFIED_SDK_VERSIONS], so an SDK upgrade fails the build first.
 *  - AAOS `SEAT_OCCUPANCY` directly is not an option: on Pleos Connect 2.1.0 (Android 14) the only permission that reads
 *    it is `CONTROL_CAR_SEATS`, signature|privileged (`pm list permissions -f`); `READ_CAR_SEATS` is not defined.
 */
object Occupancy {
    /** Pleos Vehicle SDK versions whose occupancy mapping was checked (true = AAOS VACANT). */
    val VERIFIED_SDK_VERSIONS = setOf("2.3.2")

    fun resolve(sdkValue: Boolean?, sdkVersion: String): Boolean? = when {
        sdkValue == null -> null
        sdkVersion in VERIFIED_SDK_VERSIONS -> !sdkValue
        else -> null
    }
}
