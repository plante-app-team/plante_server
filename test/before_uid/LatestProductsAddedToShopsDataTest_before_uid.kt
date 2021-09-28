package vegancheckteam.plante_server.before_uid

import io.ktor.server.testing.withTestApplication
import java.util.UUID
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.module
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator

class LatestProductsAddedToShopsDataTest_before_uid {
    @Before
    fun setUp() {
        withTestApplication({ module(testing = true) }) {
            transaction {
                ProductAtShopTable.deleteAll()
            }
        }
    }

    @Test
    fun `latest products added to shops`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorClientToken = registerModerator()
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()
            val shop3 = UUID.randomUUID().toString()
            val shop4 = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1)).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2)).jsonMap()
            assertEquals("ok", map["result"])

            var now = 1

            // Product 1 to shop 1
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmId" to shop1,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 1 to shop 2
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmId" to shop2,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 to shop 3
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop3,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 4
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop4,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 1
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop1,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify

            map = authedGet(moderatorClientToken, "/latest_products_added_to_shops_data/?", mapOf(
                "limit" to "3",
                "page" to "0"
            )).jsonMap()
            var products = (map["products_ordered"] as List<*>).map { it as Map<*, *> }
            var shops = (map["shops_ordered"] as List<*>).map { it as Map<*, *> }
            var whenAdded = map["when_added_ordered"] as List<*>
            assertEquals(3, products.size, map.toString())
            assertEquals(3, shops.size, map.toString())
            assertEquals(3, whenAdded.size, map.toString())
            assertEquals(listOf(now, now - 1, now - 2), whenAdded, map.toString())
            assertEquals(barcode2, products[0]["barcode"], map.toString())
            assertEquals(barcode2, products[1]["barcode"], map.toString())
            assertEquals(barcode2, products[2]["barcode"], map.toString())
            assertEquals(shop1, shops[0]["osm_id"], map.toString())
            assertEquals(shop4, shops[1]["osm_id"], map.toString())
            assertEquals(shop3, shops[2]["osm_id"], map.toString())

            map = authedGet(moderatorClientToken, "/latest_products_added_to_shops_data/?", mapOf(
                "limit" to "3",
                "page" to "1"
            )).jsonMap()
            products = (map["products_ordered"] as List<*>).map { it as Map<*, *> }
            shops = (map["shops_ordered"] as List<*>).map { it as Map<*, *> }
            whenAdded = map["when_added_ordered"] as List<*>
            assertEquals(2, products.size, map.toString())
            assertEquals(2, shops.size, map.toString())
            assertEquals(2, whenAdded.size, map.toString())
            assertEquals(listOf(now - 3, now - 4), whenAdded, map.toString())
            assertEquals(barcode1, products[0]["barcode"], map.toString())
            assertEquals(barcode1, products[1]["barcode"], map.toString())
            assertEquals(shop2, shops[0]["osm_id"], map.toString())
            assertEquals(shop1, shops[1]["osm_id"], map.toString())
        }
    }

    @Test
    fun `latest products added to shops without params`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorClientToken = registerModerator()
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()
            val shop3 = UUID.randomUUID().toString()
            val shop4 = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1)).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2)).jsonMap()
            assertEquals("ok", map["result"])

            var now = 1

            // Product 1 to shop 1
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmId" to shop1,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 1 to shop 2
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmId" to shop2,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 to shop 3
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop3,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 4
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop4,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify

            map = authedGet(moderatorClientToken, "/latest_products_added_to_shops_data/?").jsonMap()
            val products = (map["products_ordered"] as List<*>).map { it as Map<*, *> }
            val shops = (map["shops_ordered"] as List<*>).map { it as Map<*, *> }
            val whenAdded = map["when_added_ordered"] as List<*>
            assertEquals(4, products.size, map.toString())
            assertEquals(4, shops.size, map.toString())
            assertEquals(4, whenAdded.size, map.toString())
            assertEquals(listOf(now, now - 1, now - 2, now - 3), whenAdded, map.toString())
            assertEquals(barcode2, products[0]["barcode"], map.toString())
            assertEquals(barcode2, products[1]["barcode"], map.toString())
            assertEquals(barcode1, products[2]["barcode"], map.toString())
            assertEquals(barcode1, products[3]["barcode"], map.toString())
            assertEquals(shop4, shops[0]["osm_id"], map.toString())
            assertEquals(shop3, shops[1]["osm_id"], map.toString())
            assertEquals(shop2, shops[2]["osm_id"], map.toString())
            assertEquals(shop1, shops[3]["osm_id"], map.toString())
        }
    }

    @Test
    fun `latest products added to shops request by a normal user`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val map = authedGet(clientToken, "/latest_products_added_to_shops_data/?").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
