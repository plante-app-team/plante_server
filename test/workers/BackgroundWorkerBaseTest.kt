package vegancheckteam.plante_server.workers

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
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
    }

    @Test
    fun `idle notification when there is no work`() {
        val worker = TestedWorker()
        worker.startWorker()
        worker.waitUntilIdle()
        // In case of a fail the test will hang
    }
}

private class TestedWorker(backoffDelays: List<Long> = listOf(1)) : BackgroundWorkerBase("TestedWorker", backoffDelays) {
    private val tasks = Collections.synchronizedList(mutableListOf<Runnable>())

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

    override fun hasWork() = tasks.isNotEmpty()

    override fun doWork() {
        if (hasWork()) {
            tasks.removeFirst()?.run()
        }
    }
}
