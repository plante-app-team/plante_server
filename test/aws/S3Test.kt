package vegancheckteam.plante_server.aws

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class S3Test {
    @Suppress("BlockingMethodInNonBlockingContext")
    @Test
    fun `S3 fragile test`() = withPlanteTestApplication {
        runBlocking {
            val key = "testing"
            if (S3.getData(key) != null) {
                S3.deleteData(key)
            }
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
}
