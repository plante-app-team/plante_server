package vegancheckteam.plante_server

import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.cmds.PRODUCTS_DATA_PARAMS_PAGE_SIZE
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register

class ProductsDataTest {
    @Test
    fun `good scenario`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcodes = mutableListOf<String>()
            for (i in 1..(PRODUCTS_DATA_PARAMS_PAGE_SIZE*2.5).toInt()) {
                barcodes.add(UUID.randomUUID().toString())
                val map = authedGet(user, "/create_update_product/?barcode=${barcodes.last()}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Page 0
            val obtainedBarcodes = mutableListOf<String>()
            var map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "0")).jsonMap()
            var productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            var newBarcodes = productsJson.map { it["barcode"].toString() }
            assertEquals(PRODUCTS_DATA_PARAMS_PAGE_SIZE, newBarcodes.size)
            obtainedBarcodes.addAll(newBarcodes)
            assertNotEquals(barcodes.size, obtainedBarcodes.size)

            // Page 1
            map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "1")).jsonMap()
            productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            newBarcodes = productsJson.map { it["barcode"].toString() }
            assertEquals(PRODUCTS_DATA_PARAMS_PAGE_SIZE, newBarcodes.size)
            assertFalse(newBarcodes.any { obtainedBarcodes.contains(it) })
            obtainedBarcodes.addAll(newBarcodes)
            assertNotEquals(barcodes.size, obtainedBarcodes.size)

            // Page 2
            map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "2")).jsonMap()
            productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            newBarcodes = productsJson.map { it["barcode"].toString() }
            // Not full page
            assertNotEquals(PRODUCTS_DATA_PARAMS_PAGE_SIZE, newBarcodes.size)
            assertFalse(newBarcodes.any { obtainedBarcodes.contains(it) })
            obtainedBarcodes.addAll(newBarcodes)
            // All barcodes obtained
            assertEquals(barcodes.sorted(), obtainedBarcodes.sorted())

            // Page 3
            map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "3")).jsonMap()
            productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            newBarcodes = productsJson.map { it["barcode"].toString() }
            assertEquals(emptyList(), newBarcodes)
        }
    }

    @Test
    fun `no barcodes found`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcodes = listOf(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            )

            val map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "0")).jsonMap()
            val productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            val obtainedBarcodes = productsJson.map { it["barcode"].toString() }
            assertEquals(emptyList(), obtainedBarcodes)
        }
    }

    @Test
    fun `some barcodes are not found`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcodes = listOf(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
            )

            var map = authedGet(user, "/create_update_product/?barcode=${barcodes[0]}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/create_update_product/?barcode=${barcodes[2]}").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = mapOf("page" to "0")).jsonMap()
            val productsJson = (map["products"] as List<*>).map { it as Map<*, *> }
            val obtainedBarcodes = productsJson.map { it["barcode"].toString() }
            assertEquals(listOf(barcodes[0], barcodes[2]).sorted(), obtainedBarcodes.sorted())
        }
    }

    @Test
    fun `no page parameter provided`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcodes = listOf(
                UUID.randomUUID().toString(),
            )
            val resp = authedGet(user, "/products_data/",
                queryParamsLists = mapOf("barcodes" to barcodes),
                queryParams = emptyMap())
            assertEquals(HttpStatusCode.NotFound, resp.response.status())
        }
    }
}
