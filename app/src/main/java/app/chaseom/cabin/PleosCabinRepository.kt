package app.chaseom.cabin

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import app.chaseom.BuildConfig
import app.chaseom.Probe
import app.chaseom.stay.EnergyModel
import app.chaseom.vehicle.CarConnection
import app.chaseom.vehicle.CarUser
import app.chaseom.vehicle.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Cabin signals from the Pleos Vehicle SDK (climate, seats, doors, windows, lights, SOC) plus
 * AAOS standard properties that Pleos does not expose publicly (fuel type, battery capacity,
 * charge port). Signatures checked against Vehicle-2.3.2 public API on 2026-11-09.
 *
 * With [Probe] switched on (measurement tools only), every callback is also logged with the wall
 * clock; that is how the Virtual Console write -> app callback latency is measured.
 */
class PleosCabinRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val carConnection: CarConnection,
) : CabinRepository {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(CabinState())
    override val state: StateFlow<CabinState> = _state.asStateFlow()
    override val source = DataSource.PLEOS_SDK

    private var vehicle: Vehicle? = null
    private var propertyManager: CarPropertyManager? = null
    private var carSubscribed = false

    // Rear occupancy: mapped by the verified Pleos SDK version (see Occupancy).
    @Volatile private var pleosRearLeft: Boolean? = null
    @Volatile private var pleosRearRight: Boolean? = null
    private var pollJob: Job? = null
    private val pollGate = PollGate(start = { vehicle?.let { startPolling(it) } }, stop = ::stopPolling)
    private var powerArea: SeatArea? = null
    private var acArea: SeatArea? = null
    private var fanArea: SeatArea? = null
    private var recircArea: SeatArea? = null

    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val passenger = SeatArea(SeatValue.ROW_1_RIGHT)
    private val rearLeft = SeatArea(SeatValue.ROW_2_LEFT)
    private val rearRight = SeatArea(SeatValue.ROW_2_RIGHT)

    private val doorAreas = mapOf(
        "driver" to DoorArea(DoorValue.ROW_1_LEFT),
        "passenger" to DoorArea(DoorValue.ROW_1_RIGHT),
        "rearLeft" to DoorArea(DoorValue.ROW_2_LEFT),
        "rearRight" to DoorArea(DoorValue.ROW_2_RIGHT),
        "trunk" to DoorArea(DoorValue.REAR),
    )
    private val windowAreas = mapOf(
        "driver" to WindowArea(WindowValue.ROW_1_LEFT),
        "passenger" to WindowArea(WindowValue.ROW_1_RIGHT),
        "rearLeft" to WindowArea(WindowValue.ROW_2_LEFT),
        "rearRight" to WindowArea(WindowValue.ROW_2_RIGHT),
    )

    // ---- listeners -------------------------------------------------------------------------

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) =
            set("soc", value) { it.copy(socPercent = value, socReadable = value != null) }
        override fun onFailed(e: Exception) = fail("soc", e)
    }
    private val powerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(value: Boolean?) = set("hvacPower", value) { it.copy(hvacPowerOn = value) }
        override fun onFailed(e: Exception) = fail("hvacPower", e)
    }
    private val acListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(value: Boolean?) = set("ac", value) { it.copy(acOn = value) }
        override fun onFailed(e: Exception) = fail("ac", e)
    }
    private val fanListener = object : HvacFanSpeedListener {
        override fun onHvacFanSpeedUpdated(value: Int?) = set("fan", value) { it.copy(fanSpeed = value) }
        override fun onFailed(e: Exception) = fail("fan", e)
    }
    private val tempListener = object : HvacTemperatureValueListener {
        override fun onHvacTemperatureUpdated(celsius: Float?, other: Float?) =
            set("tempSet", "$celsius/$other") { it.copy(driverSetTempC = celsius) }
        override fun onFailed(e: Exception) = fail("tempSet", e)
    }
    private val recircListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(value: Boolean?) =
            set("recirc", value) { it.copy(recirculationOn = value) }
        override fun onFailed(e: Exception) = fail("recirc", e)
    }
    private val seatHeatListener = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(value: Int?) = set("seatHeat", value) { it.copy(driverSeatHeat = value) }
        override fun onFailed(e: Exception) = fail("seatHeat", e)
    }
    private val passengerSeatHeatListener = object : HvacSeatTemperatureListener {
        override fun onHvacSeatTemperatureUpdated(value: Int?) = set("seatHeatPas", value) { it.copy(passengerSeatHeat = value) }
        override fun onFailed(e: Exception) = fail("seatHeatPas", e)
    }
    private val driverBackrestListener = object : SeatBackrestAnglePositionListener {
        override fun onSeatBackrestAnglePositionUpdated(value: Int?) =
            set("backrestDriver", value) { it.copy(driverBackrest = value) }
        override fun onFailed(e: Exception) = fail("backrestDriver", e)
    }
    private val passengerBackrestListener = object : SeatBackrestAnglePositionListener {
        override fun onSeatBackrestAnglePositionUpdated(value: Int?) =
            set("backrestPassenger", value) { it.copy(passengerBackrest = value) }
        override fun onFailed(e: Exception) = fail("backrestPassenger", e)
    }
    private val rearLeftOccListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(value: Boolean?) {
            pleosRearLeft = value
            set("occRearLeft", value) { it.withRear() }
        }
        override fun onFailed(e: Exception) = fail("occRearLeft", e)
    }
    private val rearRightOccListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(value: Boolean?) {
            pleosRearRight = value
            set("occRearRight", value) { it.withRear() }
        }
        override fun onFailed(e: Exception) = fail("occRearRight", e)
    }
    private val outsideCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val c = (value.value as? Float)?.takeIf { value.status == CAR_STATUS_AVAILABLE }
            setOutside(c)
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = setOutside(null)
    }
    private val doorListeners = doorAreas.keys.associateWith { key ->
        object : DoorPositionListener {
            override fun onPositionUpdated(value: Int?) {
                val open = doorOpenFromPosition(value)
                val apply = { set("door.$key", value) { it.withDoor(key, open) } }
                // The receipt is built the moment the driver door opens, from the state at that moment.
                // The light switch is polled once a second, so read it again first (a light switched
                // off just before the door opened must not show as "on").
                if (key == "driver" && open == true && _state.value.driverDoorOpen != true) readLightThen(apply) else apply()
            }
            override fun onFailed(e: Exception) = fail("door.$key", e)
        }
    }
    private val windowListeners = windowAreas.keys.associateWith { key ->
        object : WindowPositionListener {
            override fun onWindowPositionUpdated(value: Int?) = set("window.$key", value) {
                it.copy(windows = it.windows + (key to (value ?: WINDOW_UNKNOWN)))
            }
            override fun onFailed(e: Exception) = fail("window.$key", e)
        }
    }
    private val portCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val open = value.value as? Boolean
            set("chargePort", open) { it.copy(chargePortOpen = open) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = Unit
    }

    // ---- lifecycle -------------------------------------------------------------------------

    @Synchronized
    override fun start() {
        if (vehicle != null) return
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            Log.e(TAG, "Vehicle.initialize failed", t)
            _state.update { it.copy(connected = false, lastError = t.message) }
            return
        }
        vehicle = v
        // "connected" turns true on the first answer from the Pleos service (see [set]), not right after initialize().
        _state.update { it.copy(lastError = null) }

        guard("soc") {
            v.getEvBattery().getEvBatteryStateOfCharge({ s -> socListener.onEvBatteryStateOfChargeUpdated(s) }, { fail("soc", it) })
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        }
        guard("efficiency") {
            v.getEvBattery().getCumulativeEvEfficiency(
                { e -> set("efficiency", e) { it.copy(evEfficiencyKmPerKwh = e?.takeIf { x -> x > 0f }) } },
                { fail("efficiency", it) },
            )
        }
        val hvac = v.getHVAC()
        // HVAC power / AC / fan / recirculation are not per-seat on every vehicle (the Pleos
        // emulator rejects area 0x1 for them). Resolve the supported area from capabilities.
        guard("hvacPower") {
            hvac.checkHvacPowerCapabilities({ _, areas ->
                val a = pickArea(areas).also { powerArea = it }
                probe("hvacPowerArea", areaName(a))
                guard("hvacPower") {
                    hvac.getHvacPowerState(a, { powerListener.onHvacPowerStateUpdated(it) }, { fail("hvacPower", it) })
                    hvac.registerHvacPowerState(a, powerListener)
                }
            }, { fail("hvacPowerCaps", it) })
        }
        guard("ac") {
            hvac.checkHvacAcCapabilities({ _, areas ->
                val a = pickArea(areas).also { acArea = it }
                guard("ac") {
                    hvac.getHvacAcState(a, { acListener.onHvacAcStateUpdated(it) }, { fail("ac", it) })
                    hvac.registerHvacAcState(a, acListener)
                }
            }, { fail("acCaps", it) })
        }
        guard("fan") {
            hvac.checkHvacFanSpeedCapabilities({ _, bounds ->
                val a = pickArea(bounds.keys.toList()).also { fanArea = it }
                probe("fanBounds", bounds.values.firstOrNull()?.let { "${it.minValue}..${it.maxValue}" })
                guard("fan") {
                    hvac.getHvacFanSpeed(a, { fanListener.onHvacFanSpeedUpdated(it) }, { fail("fan", it) })
                    hvac.registerHvacFanSpeed(a, fanListener)
                }
            }, { fail("fanCaps", it) })
        }
        guard("recirc") {
            hvac.checkHvacRecirculationCapabilities({ _, areas ->
                val a = pickArea(areas).also { recircArea = it }
                guard("recirc") {
                    hvac.getHvacRecirculationState(a, { recircListener.onHvacAutoRecirculationStateUpdated(it) }, { fail("recirc", it) })
                    hvac.registerHvacRecirculationState(a, recircListener)
                }
            }, { fail("recircCaps", it) })
        }
        guard("tempSet") { hvac.registerHvacTemperature(driver, tempListener) }
        val seat = v.getSeat()
        guard("seatHeat") {
            seat.getHvacSeatTemperature(driver, { seatHeatListener.onHvacSeatTemperatureUpdated(it) }, { fail("seatHeat", it) })
            seat.registerHvacSeatTemperature(driver, seatHeatListener)
        }
        guard("seatHeatPas") {
            seat.getHvacSeatTemperature(passenger, { passengerSeatHeatListener.onHvacSeatTemperatureUpdated(it) }, { fail("seatHeatPas", it) })
            seat.registerHvacSeatTemperature(passenger, passengerSeatHeatListener)
        }
        guard("backrestCaps") {
            seat.checkSeatBackrestAnglePositionCapabilities(
                { config, bounds ->
                    val b = bounds[driver]
                    set("backrestCaps", "${config.accessType} $bounds") {
                        it.copy(backrestMin = b?.minValue, backrestMax = b?.maxValue)
                    }
                },
                { fail("backrestCaps", it) },
            )
        }
        guard("backrestDriver") {
            seat.getCurrentSeatBackrestAnglePosition(driver, { driverBackrestListener.onSeatBackrestAnglePositionUpdated(it) }, { fail("backrestDriver", it) })
            seat.registerSeatBackrestAnglePosition(driver, driverBackrestListener)
        }
        guard("backrestPassenger") {
            seat.getCurrentSeatBackrestAnglePosition(passenger, { passengerBackrestListener.onSeatBackrestAnglePositionUpdated(it) }, { fail("backrestPassenger", it) })
            seat.registerSeatBackrestAnglePosition(passenger, passengerBackrestListener)
        }
        guard("occRearLeft") { seat.registerSeatOccupancy(rearLeft, rearLeftOccListener) }
        guard("occRearRight") { seat.registerSeatOccupancy(rearRight, rearRightOccListener) }
        doorAreas.forEach { (key, area) -> guard("door.$key") { v.getDoor().registerDoorPosition(area, doorListeners.getValue(key)) } }
        windowAreas.forEach { (key, area) -> guard("window.$key") { v.getWindow().registerWindowPosition(area, windowListeners.getValue(key)) } }

        if (!carSubscribed) {
            carSubscribed = true
            carConnection.subscribe(carUser)
        }
        pollGate.ready(true)
    }

    /**
     * Switch / lever / backrest polling runs only while a parked stay is open; nothing reads those
     * values outside a stay. The values are cleared when polling stops, so an old reading is never
     * used when the next stay starts (the first poll runs at once).
     */
    @Synchronized
    override fun setPolling(active: Boolean) = pollGate.want(active)

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(cabinLightOn = null, readingLightOn = null, wiperAuto = null, wiperOn = null) }
    }

    @Synchronized
    override fun stop() {
        pollGate.ready(false)
        val v = vehicle ?: return
        guard("unregister") {
            v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            val hvac = v.getHVAC()
            powerArea?.let { hvac.unregisterHvacPowerState(it, powerListener) }
            acArea?.let { hvac.unregisterHvacAcState(it, acListener) }
            fanArea?.let { hvac.unregisterHvacFanSpeed(it, fanListener) }
            hvac.unregisterHvacTemperature(driver, tempListener)
            recircArea?.let { hvac.unregisterHvacRecirculationState(it, recircListener) }
            val seat = v.getSeat()
            seat.unregisterHvacSeatTemperature(driver, seatHeatListener)
            seat.unregisterHvacSeatTemperature(passenger, passengerSeatHeatListener)
            seat.unregisterSeatBackrestAnglePosition(driver, driverBackrestListener)
            seat.unregisterSeatBackrestAnglePosition(passenger, passengerBackrestListener)
            seat.unregisterSeatOccupancy(rearLeft, rearLeftOccListener)
            seat.unregisterSeatOccupancy(rearRight, rearRightOccListener)
            doorAreas.forEach { (k, a) -> v.getDoor().unregisterDoorPosition(a, doorListeners.getValue(k)) }
            windowAreas.forEach { (k, a) -> v.getWindow().unregisterWindowPosition(a, windowListeners.getValue(k)) }
        }
        guard("release") { v.release() }
        vehicle = null
        try {
            propertyManager?.let { pm ->
                pm.unregisterCallback(portCallback)
                pm.unregisterCallback(outsideCallback)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "car unregister: ${t.message}")
        }
        propertyManager = null
        _state.update { it.copy(connected = false) }
    }

    // ---- AAOS standard properties ------------------------------------------------------------

    /** Follows the shared car connection: registers on ready, drops the manager when the car service goes away. */
    private val carUser = object : CarUser {
        override fun onReady(handle: Car) = startCarProperties(handle)
        override fun onLost() {
            propertyManager = null
            _state.update { it.copy(outsideTempC = null) }
        }
    }

    private fun startCarProperties(c: Car) {
        try {
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
            propertyManager = cpm
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = cpm.getProperty<Array<Int>>(VehiclePropertyIds.INFO_FUEL_TYPE, 0)?.value
                val types = raw?.toList()
                set("fuelType", types) { it.copy(fuelTypes = types, fuelTypeReadFailed = types == null) }
            } catch (t: Throwable) {
                fail("fuelType", t)
                _state.update { it.copy(fuelTypeReadFailed = true) }
            }
            try {
                val wh = cpm.getProperty<Float>(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, 0)?.value
                // AAOS defines this in Wh; guard against implementations that report kWh.
                val kwh = wh?.let { if (it > 1000f) it / 1000f else it }?.takeIf { it > 0f }
                set("capacity", "$wh -> $kwh kWh") { it.copy(batteryCapacityKwh = kwh) }
            } catch (t: Throwable) {
                fail("capacity", t)
            }
            try {
                cpm.registerCallback(portCallback, VehiclePropertyIds.EV_CHARGE_PORT_OPEN, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            } catch (t: Throwable) {
                fail("chargePort", t)
            }
            // Outside temperature (CAR_EXTERIOR_ENVIRONMENT, normal permission): corrects the heating estimate.
            // Unreadable -> null -> the model keeps its -10 C assumption.
            try {
                val p = cpm.getProperty<Float>(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)
                probe("outsideTempRaw", "${p?.value} status=${p?.status}")
                setOutside(p?.value?.takeIf { p.status == CAR_STATUS_AVAILABLE })
            } catch (t: Throwable) {
                fail("outsideTemp", t)
                setOutside(null)
            }
            // Separate try: a refused subscription must not throw away the value just read.
            try {
                cpm.registerCallback(outsideCallback, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, OUTSIDE_RATE_HZ)
            } catch (t: Throwable) {
                fail("outsideTempSub", t)
            }
        } catch (t: Throwable) {
            fail("car", t)
        }
    }

    /**
     * Light switches, the wiper lever and the backrest have no listener the Virtual Console drives
     * reliably, so they are read once a second, only during a parked stay (see [setPolling]).
     */
    private fun startPolling(v: Vehicle) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                guard("cabinLight") {
                    v.getLight().getCabinLightSwitch(
                        { s -> setIfChanged("cabinLight", s == LightSwitch.ON) { st, on -> (st.cabinLightOn != on) to st.copy(cabinLightOn = on) } },
                        { fail("cabinLight", it) },
                    )
                }
                guard("readingLight") {
                    v.getLight().getReadingLightSwitch(
                        driver,
                        { s -> setIfChanged("readingLight", s == LightSwitch.ON) { st, on -> (st.readingLightOn != on) to st.copy(readingLightOn = on) } },
                        { fail("readingLight", it) },
                    )
                }
                // Wiper: lever by polling, wiping state read in the same tick. A read
                // failure leaves the value null, and null never counts as rain.
                guard("wiper") {
                    v.getWiper().getCurrentWiperSwitch(
                        frontWindshield,
                        { sw -> setIfChanged("wiperAuto", sw == WiperSwitch.AUTO) { st, x -> (st.wiperAuto != x) to st.copy(wiperAuto = x) } },
                        { fail("wiperSwitch", it) },
                    )
                    v.getWiper().getCurrentWiperState(
                        frontWindshield,
                        { ws -> setIfChanged("wiperOn", ws == WiperState.ON) { st, x -> (st.wiperOn != x) to st.copy(wiperOn = x) } },
                        { fail("wiperState", it) },
                    )
                }
                // Backrest: the emulator's position write does not always raise ON_CHANGE events,
                // so the current value is also polled.
                guard("backrestPoll") {
                    v.getSeat().getCurrentSeatBackrestAnglePosition(
                        driver,
                        { b -> setIfChanged("backrestDriverPoll", b) { st, x -> (st.driverBackrest != x) to st.copy(driverBackrest = x) } },
                        { fail("backrestDriverPoll", it) },
                    )
                    v.getSeat().getCurrentSeatBackrestAnglePosition(
                        passenger,
                        { b -> setIfChanged("backrestPassengerPoll", b) { st, x -> (st.passengerBackrest != x) to st.copy(passengerBackrest = x) } },
                        { fail("backrestPassengerPoll", it) },
                    )
                }
                delay(POLL_MS)
            }
        }
    }

    /**
     * Reads the cabin light switch once, then runs [then]. If the read fails or does not answer within
     * [LIGHT_READ_TIMEOUT_MS], the light becomes unknown (the receipt says "확인 안 됨"), never the older value.
     */
    private fun readLightThen(then: () -> Unit) {
        val v = vehicle
        if (v == null) { then(); return }
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val once = { if (done.compareAndSet(false, true)) then() }
        try {
            v.getLight().getCabinLightSwitch(
                { sw -> setIfChanged("cabinLight", sw == LightSwitch.ON) { st, on -> (st.cabinLightOn != on) to st.copy(cabinLightOn = on) }; once() },
                { fail("cabinLight", it); lightUnknownThen(once) },
            )
        } catch (t: Throwable) {
            fail("cabinLight", t)
            lightUnknownThen(once)
        }
        scope.launch {
            delay(LIGHT_READ_TIMEOUT_MS)
            if (!done.get()) lightUnknownThen(once)
        }
    }

    private fun lightUnknownThen(then: () -> Unit) {
        _state.update { it.copy(cabinLightOn = null) }
        then()
    }

    // ---- helpers ---------------------------------------------------------------------------

    private inline fun set(name: String, value: Any?, crossinline transform: (CabinState) -> CabinState) {
        probe(name, value)
        // "connected" = the Pleos service has answered with a value at least once.
        _state.update { transform(it).let { s -> if (s.connected || value == null) s else s.copy(connected = true) } }
    }

    /** Stored to 0.5 C so a CONTINUOUS sensor does not recompose the screen on every tiny change. */
    private fun setOutside(celsius: Float?) {
        val c = EnergyModel.validOutsideC(celsius)?.let { Math.round(it * 2f) / 2f }
        setIfChanged("outsideTemp", c) { st, x -> (st.outsideTempC != x) to st.copy(outsideTempC = x) }
    }

    private fun CabinState.withRear(): CabinState = copy(
        rearLeftOccupied = Occupancy.resolve(pleosRearLeft, BuildConfig.PLEOS_VEHICLE_SDK),
        rearRightOccupied = Occupancy.resolve(pleosRearRight, BuildConfig.PLEOS_VEHICLE_SDK),
    )

    private fun <T> setIfChanged(name: String, value: T, transform: (CabinState, T) -> Pair<Boolean, CabinState>) {
        _state.update { st ->
            val (changed, next) = transform(st, value)
            if (changed) probe(name, value)
            next
        }
    }

    /** Prefer an area that covers the driver seat, otherwise the first supported one. */
    private fun pickArea(areas: List<SeatArea>): SeatArea =
        areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() ?: driver

    private fun areaName(a: SeatArea) = a.values.joinToString("|")

    private fun probe(name: String, value: Any?) = Probe.log("$name=$value")

    private fun fail(name: String, e: Throwable) {
        Log.w(TAG, "$name failed: ${e.javaClass.simpleName}")
        _state.update { it.copy(lastError = "$name: ${e.message}") }
    }

    private inline fun guard(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            fail(name, t)
        }
    }

    companion object {
        private const val TAG = "PleosCabinRepo"
        private const val POLL_MS = 1000L
        private const val LIGHT_READ_TIMEOUT_MS = 300L

        /** Outside temperature changes slowly; the slowest useful rate (the platform clamps to the sensor's min). */
        private const val OUTSIDE_RATE_HZ = 0.1f
        private const val CAR_STATUS_AVAILABLE = 0
    }
}
