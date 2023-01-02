package vegancheckteam.plante_server.cmds.likes

import io.ktor.server.testing.TestApplicationEngine
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.junit.Test
import vegancheckteam.plante_server.cmds.PRODUCTS_DATA_PARAMS_PAGE_SIZE
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ProductLikesTest {
    @Test
    fun `like, unlike and get likes of a product`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var map = authedGet(user, "/create_update_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/create_update_product/?barcode=$barcode2").jsonMap()
            assertEquals("ok", map["result"])

            // Initial state
            var productsMap = getProductsMap(user, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(0, productsMap[barcode1]!!["likes_count"])
            assertEquals(0, productsMap[barcode2]!!["likes_count"])
            assertEquals(false, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(false, productsMap[barcode2]!!["liked_by_me"])
            
            // Like the first one
            map = authedGet(user, "/like_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])
            
            // The first one should be liked
            productsMap = getProductsMap(user, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(1, productsMap[barcode1]!!["likes_count"])
            assertEquals(0, productsMap[barcode2]!!["likes_count"])
            assertEquals(true, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(false, productsMap[barcode2]!!["liked_by_me"])

            // Like the second one
            map = authedGet(user, "/like_product/?barcode=$barcode2").jsonMap()
            assertEquals("ok", map["result"])

            // Both should be liked
            productsMap = getProductsMap(user, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(1, productsMap[barcode1]!!["likes_count"])
            assertEquals(1, productsMap[barcode2]!!["likes_count"])
            assertEquals(true, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(true, productsMap[barcode2]!!["liked_by_me"])

            // Unlike the first one
            map = authedGet(user, "/unlike_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])

            // Only the second one should be liked
            productsMap = getProductsMap(user, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(0, productsMap[barcode1]!!["likes_count"])
            assertEquals(1, productsMap[barcode2]!!["likes_count"])
            assertEquals(false, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(true, productsMap[barcode2]!!["liked_by_me"])
        }
    }

    @Test
    fun `likes by multiple users`() {
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var map = authedGet(user1, "/create_update_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/create_update_product/?barcode=$barcode2").jsonMap()
            assertEquals("ok", map["result"])

            // Likes!
            map = authedGet(user1, "/like_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user2, "/like_product/?barcode=$barcode1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/like_product/?barcode=$barcode2").jsonMap()
            assertEquals("ok", map["result"])

            // Check likes, using [user1]
            var productsMap = getProductsMap(user1, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(2, productsMap[barcode1]!!["likes_count"])
            assertEquals(1, productsMap[barcode2]!!["likes_count"])
            assertEquals(true, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(true, productsMap[barcode2]!!["liked_by_me"])

            // Check likes, using [user2]
            productsMap = getProductsMap(user2, listOf(barcode1, barcode2))
            assertEquals(2, productsMap.size)
            assertEquals(2, productsMap[barcode1]!!["likes_count"])
            assertEquals(1, productsMap[barcode2]!!["likes_count"])
            assertEquals(true, productsMap[barcode1]!!["liked_by_me"])
            assertEquals(false, productsMap[barcode2]!!["liked_by_me"])
        }
    }

    private fun TestApplicationEngine.getProductsMap(clientToken: String, barcodes: List<String>): Map<String, Map<*, *>> {
        val map = authedGet(clientToken, "/products_data/",
            queryParamsLists = mapOf("barcodes" to barcodes),
            queryParams = mapOf("page" to "0"),
        ).jsonMap()
        val productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
        return productsJson.associateBy { it["barcode"].toString() }
    }
}