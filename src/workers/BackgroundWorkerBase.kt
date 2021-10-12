package vegancheckteam.plante_server.workers

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import vegancheckteam.plante_server.base.Log

abstract class BackgroundWorkerBase(
        private val name: String,
        private val backoffDelays: List<Long>) {
    private var started = AtomicBoolean()
    private var active = AtomicBoolean()
    private val syncObjet = Object()
    private val idleCallbacks = Collections.synchronizedList(mutableListOf<Runnable>())

    private var lastBackoffDelay: Long? = null

    init {
        if (backoffDelays.isEmpty()) {
            throw IllegalArgumentException("backoffDelays must not be empty")
        }
    }

    fun isActive(): Boolean = active.get()

    /**
     * Starts the worker.
     * Will throw if the worker has already started.
     */
    protected fun start() {
        synchronized(syncObjet) {
            if (started.get()) {
                throw IllegalStateException("Already started")
            }
            started.set(true)
        }
        val thread = Thread { loop() }
        thread.name = name
        thread.start()
    }

    fun stop() {
        synchronized(syncObjet) {
            started.set(false)
            syncObjet.notify()
        }
    }

    /**
     * Call it when new work is added from another thread
     */
    protected fun wakeUp() {
        if (!started.get()) {
            throw IllegalStateException("Not started")
        }
        synchronized(syncObjet) {
            syncObjet.notify()
        }
    }

    /**
     * Note: runnable will be called on another thread
     */
    fun runWhenIdle(runnable: Runnable) {
        if (!started.get()) {
            throw IllegalStateException("Not started")
        }
        synchronized(syncObjet) {
            idleCallbacks.add(runnable)
        }
        // Let's wake up - if there's truly no work,
        // the callback will be called almost immediately
        wakeUp()
    }

    private fun loop() {
        active.set(true)
        while (true) {
            try {
                doWork()
                lastBackoffDelay = null // Success!

                // Let's exit if needed
                if (!started.get()) {
                    active.set(false)
                    return
                }
                // Let's wait for new work if there's no work
                synchronized(syncObjet) {
                    if (!hasWork()) {
                        callIdleCallbacks()
                        if (!hasWork()) {
                            // Let's exit if needed
                            if (!started.get()) {
                                active.set(false)
                                return
                            }
                            try {
                                Log.i("BackgroundWorkerBase ($name)", "waiting for new work")
                                syncObjet.wait()
                            } catch (e: InterruptedException) {
                                Log.w("BackgroundWorkerBase ($name)", "interruption", e)
                                // The 'started' field is used to stop
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("BackgroundWorkerBase ($name)", "work threw", e)
                if (lastBackoffDelay == null) {
                    lastBackoffDelay = backoffDelays.first()
                } else {
                    val index = backoffDelays.indexOf(lastBackoffDelay)
                    if (index < backoffDelays.size - 1) {
                        lastBackoffDelay = backoffDelays[index + 1]
                    }
                }
                Thread.sleep(lastBackoffDelay!!)
            }
        }
    }

    private fun callIdleCallbacks() {
        val callbacksCopy = idleCallbacks.toList()
        idleCallbacks.clear()
        callbacksCopy.forEach {
            try {
                it.run()
            } catch (e: Throwable) {
                Log.e("BackgroundWorkerBase ($name)", "idle callback threw", e)
            }
        }
    }

    protected abstract fun hasWork(): Boolean
    protected abstract fun doWork()
}
