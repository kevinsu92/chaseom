package app.chaseom.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Build G (b): one car connection shared by all users, with reconnect after a car service restart. */
class SharedHandleTest {

    private class Rec : SharedHandle.User<String> {
        val events = mutableListOf<String>()
        override fun onReady(handle: String) { events += "ready:$handle" }
        override fun onLost() { events += "lost" }
    }

    @Test fun userAddedBeforeReadyGetsReadyOnce() {
        val h = SharedHandle<String>(); val a = Rec(); val b = Rec()
        h.add(a); h.add(b)
        h.ready("car")
        assertEquals(listOf("ready:car"), a.events)
        assertEquals(listOf("ready:car"), b.events)
    }

    @Test fun userAddedAfterReadyIsServedAtOnce() {
        val h = SharedHandle<String>(); val a = Rec()
        h.ready("car"); h.add(a)
        assertEquals(listOf("ready:car"), a.events)
    }

    @Test fun duplicateReadyIsIgnored() {
        val h = SharedHandle<String>(); val a = Rec()
        h.add(a); h.ready("car"); h.ready("car")
        assertEquals(listOf("ready:car"), a.events)
        assertEquals(1, h.readyCount)
    }

    // Car service crash then restart: users drop managers, then register again on the same Car object.
    @Test fun lostThenReadyReconnectsEveryUser() {
        val h = SharedHandle<String>(); val a = Rec(); val b = Rec()
        h.add(a); h.add(b)
        h.ready("car"); h.lost(); h.ready("car")
        assertEquals(listOf("ready:car", "lost", "ready:car"), a.events)
        assertEquals(listOf("ready:car", "lost", "ready:car"), b.events)
        assertEquals(2, h.readyCount)
    }

    @Test fun lostTwiceNotifiesOnce() {
        val h = SharedHandle<String>(); val a = Rec()
        h.add(a); h.ready("car"); h.lost(); h.lost()
        assertEquals(listOf("ready:car", "lost"), a.events)
        assertNull(h.current)
    }

    @Test fun lostBeforeAnyReadyIsSilent() {
        val h = SharedHandle<String>(); val a = Rec()
        h.add(a); h.lost()
        assertEquals(emptyList<String>(), a.events)
    }

    @Test fun newHandleWhileUpDropsTheOldOneFirst() {
        val h = SharedHandle<String>(); val a = Rec()
        h.add(a); h.ready("car1"); h.ready("car2")
        assertEquals(listOf("ready:car1", "lost", "ready:car2"), a.events)
    }

    @Test fun oneThrowingUserDoesNotBlockTheOthers() {
        val h = SharedHandle<String>(); val b = Rec(); val errors = mutableListOf<Throwable>()
        h.onUserError = { errors += it }
        h.add(object : SharedHandle.User<String> {
            override fun onReady(handle: String) = error("boom")
            override fun onLost() = error("boom")
        })
        h.add(b)
        h.ready("car"); h.lost()
        assertEquals(listOf("ready:car", "lost"), b.events)
        assertEquals(2, errors.size)
    }
}
