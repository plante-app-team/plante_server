package vegancheckteam.plante_server.proxy

import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.multipart_proxy.MultipartProxyStorage

class MultipartProxyStorageTest {
    @Before
    fun setUp() {
        GlobalStorage.logger = NOPLogger.NOP_LOGGER
    }

    @Test
    fun `gives valid files to write to`() {
        val storage = MultipartProxyStorage(
            maxSizeBytes = 1024,
            maxLifetimeMillis = 1000,
            directory = Path("/tmp/" + UUID.randomUUID().toString()),
        )
        val file = storage.provideTempFile()!!
        file.writeText("Hello there!")

        val reopenedFile = Path(file.absolutePath)
        assertEquals("Hello there!", reopenedFile.readText())
    }

    @Test
    fun `can handle bot existing and not-existing directory`() {
        val dir1 = Path("/tmp/" + UUID.randomUUID().toString())
        val dir2 = Path("/tmp/" + UUID.randomUUID().toString())
        dir1.createDirectories()

        // Would throw if it wasn't ok
        MultipartProxyStorage(
            maxSizeBytes = 1024,
            maxLifetimeMillis = 1000,
            directory = dir1,
        )
        // Would throw if it wasn't ok
        MultipartProxyStorage(
            maxSizeBytes = 1024,
            maxLifetimeMillis = 1000,
            directory = dir2
        )
    }

    @Test
    fun `cannot handle a directory which is actually a file`() {
        val dirButFile = Path("/tmp/" + UUID.randomUUID().toString())
        dirButFile.writeText("Hello there!")

        var caught = false;
        try {
            MultipartProxyStorage(
                maxSizeBytes = 1024,
                maxLifetimeMillis = 1000,
                directory = dirButFile,
            )
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `deletes subdirectories when meets them`() {
        val dir = Path("/tmp/" + UUID.randomUUID().toString())
        dir.createDirectories()

        val subDir = Path(dir.absolutePathString(), "subdir")
        subDir.createDirectories()
        val fileInSubDir = Path(subDir.absolutePathString(), "hello");
        fileInSubDir.writeText("Hello there!")

        assertTrue(subDir.exists())
        assertTrue(fileInSubDir.exists())

        val storage = MultipartProxyStorage(
            maxSizeBytes = 1024,
            maxLifetimeMillis = 1000,
            directory = dir,
        )
        storage.provideTempFile()

        assertFalse(subDir.exists())
        assertFalse(fileInSubDir.exists())
    }

    @Test
    fun `deletes timed-out files`() {
        val maxLifetime = 100L
        val storage = MultipartProxyStorage(
            maxSizeBytes = 1024,
            maxLifetimeMillis = maxLifetime,
            directory = Path("/tmp/" + UUID.randomUUID().toString()),
        )
        val file1 = storage.provideTempFile()!!
        file1.writeText("Hello")

        Thread.sleep(maxLifetime / 2 + 1)
        val file2 = storage.provideTempFile()!!
        file2.writeText("there")

        assertTrue(file1.exists())
        assertTrue(file2.exists())

        Thread.sleep(maxLifetime / 2 + 1)
        val file3 = storage.provideTempFile()!!
        file3.writeText("!")

        assertFalse(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())
    }

    @Test
    fun `doesn't give files when max capacity reached, but gives when old files deleted`() {
        val maxCapacity = 128
        val storage = MultipartProxyStorage(
            maxSizeBytes = maxCapacity.toLong(),
            maxLifetimeMillis = 1000000,
            directory = Path("/tmp/" + UUID.randomUUID().toString()),
        )
        val file1 = storage.provideTempFile()!!
        file1.writeText("a".repeat(maxCapacity / 2))

        val file2 = storage.provideTempFile()!!
        file2.writeText("b".repeat(maxCapacity / 2 + 1))

        val file3 = storage.provideTempFile()
        assertNull(file3)

        file1.delete()

        val file4 = storage.provideTempFile()
        assertNotNull(file4)
    }
}
