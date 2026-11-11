package app.chaseom.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollGateTest {

    private val log = mutableListOf<String>()
    private val gate = PollGate(start = { log += "start" }, stop = { log += "stop" })

    @Test fun pollsOnlyWhileAStayIsOpenAndTheCarIsConnected() {
        gate.want(true)
        assertFalse("no polling before the vehicle is connected", gate.running)
        gate.ready(true)
        assertTrue(gate.running)
        gate.want(true) // repeated calls do not restart
        assertEquals(listOf("start"), log)
    }

    @Test fun leavingPStopsAndParkingAgainStarts() {
        gate.ready(true)
        assertFalse("connected but no stay: no polling", gate.running)
        gate.want(true) // P, stay open
        gate.want(false) // D, or the stay ended
        assertFalse(gate.running)
        gate.want(true) // P again
        assertEquals(listOf("start", "stop", "start"), log)
    }

    @Test fun disconnectStopsPolling() {
        gate.ready(true); gate.want(true)
        gate.ready(false)
        assertFalse(gate.running)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test fun unreadableDoorIsUnknownNotClosed() {
        assertEquals(true, doorOpenFromPosition(3))
        assertEquals(false, doorOpenFromPosition(0))
        assertEquals(null, doorOpenFromPosition(null))
        val s = CabinState().withDoor("driver", false).withDoor("trunk", true)
        val unknown = s.withDoor("driver", null).withDoor("trunk", null)
        assertEquals(null, unknown.driverDoorOpen)
        assertFalse("unknown trunk is not drawn as open", unknown.doorsOpen.containsKey("trunk"))
    }
}
