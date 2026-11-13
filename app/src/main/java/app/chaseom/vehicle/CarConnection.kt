package app.chaseom.vehicle

import android.car.Car
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * One shared connection handle with ready / lost events, and the users that follow it.
 * No Android types, so the reconnect rules are JVM-tested (SharedHandleTest).
 *
 *  - A user added while the handle is up gets [User.onReady] at once.
 *  - [ready] with the same handle twice is ignored. [ready] with a new handle while one is up first
 *    calls [User.onLost] (the old managers are dropped), then [User.onReady] with the new one.
 *  - [lost] calls [User.onLost] once; a second [lost] does nothing.
 *  - One user that throws does not stop the others.
 *  - Adding the same user twice is ignored.
 *  - Callbacks run after the lock is released (a user may take its own lock; no lock-order inversion).
 */
class SharedHandle<T : Any> {
    interface User<T> {
        fun onReady(handle: T)
        fun onLost()
    }

    private val users = mutableListOf<User<T>>()
    @Volatile var current: T? = null
        private set
    /** How many times the handle came up; 2 or more means it reconnected at least once. */
    var readyCount = 0
        private set

    private val lock = Any()

    fun add(user: User<T>) {
        val h = synchronized(lock) {
            if (users.any { it === user }) return
            users += user
            current
        }
        h?.let { safe { user.onReady(it) } }
    }

    /** The user gets no more callbacks; it already dropped its own managers. */
    fun remove(user: User<T>) {
        synchronized(lock) { users.removeAll { it === user } }
    }

    fun ready(handle: T) {
        val (hadOld, snapshot) = synchronized(lock) {
            if (current === handle) return
            val old = current != null
            current = handle
            readyCount++
            old to users.toList()
        }
        if (hadOld) snapshot.forEach { u -> safe { u.onLost() } }
        snapshot.forEach { u -> safe { u.onReady(handle) } }
    }

    fun lost() {
        val snapshot = synchronized(lock) {
            if (current == null) return
            current = null
            users.toList()
        }
        snapshot.forEach { u -> safe { u.onLost() } }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            onUserError?.invoke(t)
        }
    }

    /** Hook for logging a user that threw (Android Log is not available in JVM tests). */
    var onUserError: ((Throwable) -> Unit)? = null
}

/**
 * The app's only connection to the AAOS car service (gear, cabin properties, UX restrictions).
 * Uses the lifecycle form of `Car.createCar`: when the car service crashes or restarts, the platform calls
 * `onLifecycleChanged(car, false)`, every user drops its managers (gear becomes UNKNOWN, which the
 * driving gate treats as driving), and on `onLifecycleChanged(car, true)` every user registers again.
 * Before build G there were three separate synchronous `createCar` calls and no reconnect.
 */
class CarConnection(context: Context) {
    private val appContext = context.applicationContext
    private val shared = SharedHandle<Car>().apply { onUserError = { Log.w(TAG, "car user failed: ${it.message}") } }
    private var car: Car? = null

    val readyCount: Int get() = shared.readyCount

    fun subscribe(user: SharedHandle.User<Car>) = shared.add(user)

    fun unsubscribe(user: SharedHandle.User<Car>) = shared.remove(user)

    @Synchronized
    fun start() {
        if (car != null) return
        car = try {
            Car.createCar(appContext, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                Log.i(TAG, "car service ready=$ready")
                if (ready) shared.ready(c) else shared.lost()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "car service unavailable: ${t.message}")
            null
        }
    }

    @Synchronized
    fun stop() {
        shared.lost()
        try {
            car?.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect: ${t.message}")
        }
        car = null
    }

    private companion object {
        const val TAG = "CarConnection"
    }
}

/** A user of the shared car connection. */
typealias CarUser = SharedHandle.User<Car>
