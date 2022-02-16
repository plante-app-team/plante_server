package vegancheckteam.plante_server.workers

import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import vegancheckteam.plante_server.GlobalStorage

class BackgroundWorkerBaseTest {
    @Before
    fun setUp() {
        GlobalStorage.logger = NOPLogger.NOP_LOGGER
    }

    @Test
    fun `can start and stop`() {
        val worker = TestedWorker()
        assertFalse(worker.isActive())

        worker.startWorker()
        @Suppress("ControlFlowWithEmptyBody")
        while (!worker.isActive());
        assertTrue(worker.isActive())

        worker.stop()
        @Suppress("ControlFlowWithEmptyBody")
        while (worker.isActive());
        assertFalse(worker.isActive())
    }

    @Test
    fun `background execution`() {
        val worker = TestedWorker()
        worker.startWorker()

        val workedOnList = Collections.synchronizedList(mutableListOf<Int>())
        for (i in 1..10) {
            worker.addWork { workedOnList.add(i) }
        }
        assertTrue(workedOnList.size < 10, "${workedOnList.size}")

        worker.waitUntilIdle()
        assertEquals(workedOnList.size, 10)

        worker.stop()
    }

    @Test
    fun `when background execution throws next background task works anyways`() {
        val worker = TestedWorker()
        worker.startWorker()

        val workedOnList = Collections.synchronizedList(mutableListOf<Int>())
        for (i in 1..20) {
            worker.addWork {
                if (i % 2 == 0) {
                    workedOnList.add(i)
                } else {
                    throw Exception("Hello there")
                }
            }
        }
        assertTrue(workedOnList.size < 10, "${workedOnList.size}")

        worker.waitUntilIdle()
        assertEquals(workedOnList.size, 10)

        worker.stop()
    }

    @Test
    fun `backoff delays`() {
        val worker = TestedWorker(backoffDelays = listOf(100, 400))
        worker.startWorker()

        var workDone = false
        worker.addWork { throw Exception("Let's cause a backoff") }
        worker.addWork { workDone = true }
        assertFalse(workDone)
        Thread.sleep(50)
        assertFalse(workDone)
        Thread.sleep(100)
        assertTrue(workDone)

        workDone = false
        worker.addWork { throw Exception("Let's cause a backoff") }
        worker.addWork { throw Exception("Let's cause a backoff twice") }
        worker.addWork { workDone = true }
        assertFalse(workDone)
        Thread.sleep(300)
        assertFalse(workDone)
        Thread.sleep(300)
        assertTrue(workDone)

        worker.stop()
    }

    @Test
    fun `idle notification when there is no work`() {
        val worker = TestedWorker()
        worker.startWorker()
        worker.waitUntilIdle()
        // In case of a fail the test will hang

        worker.stop()
    }

    @Test
    fun `auto repeat mechanics`() {
        var lastWorkCreatedTime = Instant.now().toEpochMilli()
        val timeBeforeNewWorkAppears = 300L

        var workDoneTimes = 0

        val workPreparer = {
            val now = Instant.now().toEpochMilli()
            if (timeBeforeNewWorkAppears <= now - lastWorkCreatedTime) {
                lastWorkCreatedTime = now
                Runnable { workDoneTimes += 1 }
            } else {
                null
            }
        }

        val worker = TestedWorker(
            autoRepeatPeriodMillis = timeBeforeNewWorkAppears,
            workPreparer = workPreparer)
        worker.startWorker()

        // Immediately after start the work is not done yet, because
        // new work appears each [timeBeforeNewWorkAppears] millis.
        worker.waitUntilIdle()
        assertEquals(0, workDoneTimes)

        // After half of [timeBeforeNewWorkAppears], the work still not done.
        Thread.sleep(timeBeforeNewWorkAppears / 2)
        worker.waitUntilIdle()
        assertEquals(0, workDoneTimes)

        // Now [timeBeforeNewWorkAppears] has passed, the work
        // is expected to be done.
        Thread.sleep(timeBeforeNewWorkAppears / 2)
        worker.waitUntilIdle()
        assertEquals(1, workDoneTimes)

        // After 1 + 1/2 of [timeBeforeNewWorkAppears] passed, the work is not done
        // for the second time yet
        Thread.sleep(timeBeforeNewWorkAppears / 2)
        worker.waitUntilIdle()
        assertEquals(1, workDoneTimes)

        // Now [timeBeforeNewWorkAppears] has passed the second time.
        Thread.sleep(timeBeforeNewWorkAppears / 2)
        worker.waitUntilIdle()
        assertEquals(2, workDoneTimes)

        worker.stop()
    }

    @Test
    fun `auto repeat won't happen if the period param is not provided`() {
        var lastWorkCreatedTime = Instant.now().toEpochMilli()
        val timeBeforeNewWorkAppears = 300L

        var workDoneTimes = 0

        val workPreparer = {
            val now = Instant.now().toEpochMilli()
            if (timeBeforeNewWorkAppears <= now - lastWorkCreatedTime) {
                lastWorkCreatedTime = now
                Runnable { workDoneTimes += 1 }
            } else {
                null
            }
        }

        val worker = TestedWorker(
            autoRepeatPeriodMillis = null,
            workPreparer = workPreparer)
        worker.startWorker()
        worker.waitUntilIdle()

        // Even after we wait 2x of [timeBeforeNewWorkAppears], no work is done,
        // because we have passed [null] as [autoRepeatPeriodMillis].
        Thread.sleep(timeBeforeNewWorkAppears * 2)
        assertEquals(0, workDoneTimes)
    }
}

private class TestedWorker(
    backoffDelays: List<Long> = listOf(1),
    val autoRepeatPeriodMillis: Long? = null,
    val workPreparer: (() -> Runnable?)? = null)
        : BackgroundWorkerBase("TestedWorker", backoffDelays) {
    private val tasks = Collections.synchronizedList(mutableListOf<Runnable>())

    override fun autoRepeatPeriodMillis() = autoRepeatPeriodMillis

    fun startWorker() {
        super.start()
    }

    fun addWork(task: Runnable) {
        tasks.add(task)
        wakeUp()
    }

    fun waitUntilIdle() {
        val called = AtomicBoolean()
        runWhenIdle {
            called.set(true)
        }
        @Suppress("ControlFlowWithEmptyBody")
        while (!called.get());
    }

    override fun maybePrepareNewWork() {
        val newWork = workPreparer?.invoke()
        if (newWork != null) {
            tasks.add(newWork)
        }
    }

    override fun hasWork() = tasks.isNotEmpty()

    override fun doWork() {
        if (hasWork()) {
            tasks.removeFirst()?.run()
        }
    }
}
