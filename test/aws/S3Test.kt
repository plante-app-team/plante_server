package vegancheckteam.plante_server.aws

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class S3Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            runBlocking {
                for (key in S3.listKeys(prefix = "").toList()) {
                    S3.deleteData(key)
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 fragile test`() = withPlanteTestApplication {
        runBlocking {
            val key = "testing"
            assertNull(S3.getData(key))

            val data = "Hello there"
            S3.putData(key, data.byteInputStream())

            val receivedData = S3.getData(key)!!
            val receivedStr = String(receivedData.readAllBytes())
            receivedData.close()
            assertEquals(data, receivedStr, receivedStr)

            S3.deleteData(key)
            assertNull(S3.getData(key))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 can overwrite data fragile test`() = withPlanteTestApplication {
        runBlocking {
            val key = "testing"
            assertNull(S3.getData(key))

            val data = "Hello there"
            S3.putData(key, data.byteInputStream())

            var receivedData = S3.getData(key)!!
            var receivedStr = String(receivedData.readAllBytes())
            receivedData.close()
            assertEquals(data, receivedStr, receivedStr)

            val data2 = "Hello there 2"
            assertNotEquals(data, data2)
            S3.putData(key, data2.byteInputStream())

            receivedData = S3.getData(key)!!
            receivedStr = String(receivedData.readAllBytes())
            receivedData.close()
            assertEquals(data2, receivedStr, receivedStr)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 max size fragile test`() = withPlanteTestApplication {
        runBlocking {
            val key = "testing"
            assertNull(S3.getData(key))

            val data = "Hello there"
            var caught = false
            try {
                S3.putData(key, data.byteInputStream(), maxBytes = data.length - 1)
            } catch (e: Exception) {
                caught = true
            }

            assertTrue(caught)
            assertNull(S3.getData(key))

            caught = false
            try {
                S3.putData(key, data.byteInputStream(), maxBytes = data.length)
            } catch (e: Exception) {
                caught = true
            }

            assertFalse(caught)
            val receivedData = S3.getData(key)!!
            val receivedStr = String(receivedData.readAllBytes())
            receivedData.close()
            assertEquals(data, receivedStr, receivedStr)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 list all keys fragile test`() = withPlanteTestApplication {
        runBlocking {
            var keys = S3.listKeys(prefix = "").toList()
            assertEquals(emptyList(), keys)

            val key1 = "key1"
            val key2 = "key2"
            val data = "Hello there"

            S3.putData(key1, data.byteInputStream())
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(listOf(key1), keys)

            S3.putData(key2, data.byteInputStream())
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(listOf(key1, key2), keys)

            keys = S3.listKeys(prefix = "").toList()
            for (key in keys) {
                S3.deleteData(key)
            }
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(emptyList(), keys)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 list subset of keys fragile test`() = withPlanteTestApplication {
        runBlocking {
            var keys = S3.listKeys(prefix = "").toList()
            assertEquals(emptyList(), keys)

            val prefix1 = "prefix1"
            val prefix2 = "prefix2"
            val key1 = "$prefix1/key1"
            val key2 = "$prefix2/key2"
            val data = "Hello there"

            S3.putData(key1, data.byteInputStream())
            keys = S3.listKeys(prefix = prefix1).toList()
            assertEquals(listOf(key1), keys)
            keys = S3.listKeys(prefix = prefix2).toList()
            assertEquals(emptyList(), keys)

            S3.putData(key2, data.byteInputStream())
            keys = S3.listKeys(prefix = prefix1).toList()
            assertEquals(listOf(key1), keys)
            keys = S3.listKeys(prefix = prefix2).toList()
            assertEquals(listOf(key2), keys)
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(listOf(key1, key2), keys)

            keys = S3.listKeys(prefix = prefix1).toList()
            for (key in keys) {
                S3.deleteData(key)
            }
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(listOf(key2), keys)

            keys = S3.listKeys(prefix = prefix2).toList()
            for (key in keys) {
                S3.deleteData(key)
            }
            keys = S3.listKeys(prefix = "").toList()
            assertEquals(emptyList(), keys)
        }
    }
}
