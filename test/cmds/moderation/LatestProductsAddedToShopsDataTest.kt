package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class LatestProductsAddedToShopsDataTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ProductAtShopTable.deleteAll()
            }
        }
    }

    @Test
    fun `latest products added to shops`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()
            val shop3 = generateFakeOsmUID()
            val shop4 = generateFakeOsmUID()

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
                "shopOsmUID" to shop1.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 1 to shop 2
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 to shop 3
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop3.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 4
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop4.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 1
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop1.asStr,
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
            assertEquals(shop1.asStr, shops[0]["osm_uid"], map.toString())
            assertEquals(shop4.asStr, shops[1]["osm_uid"], map.toString())
            assertEquals(shop3.asStr, shops[2]["osm_uid"], map.toString())

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
            assertEquals(shop2.asStr, shops[0]["osm_uid"], map.toString())
            assertEquals(shop1.asStr, shops[1]["osm_uid"], map.toString())
        }
    }

    @Test
    fun `latest products added to shops without params`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()
            val shop3 = generateFakeOsmUID()
            val shop4 = generateFakeOsmUID()

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
                "shopOsmUID" to shop1.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 1 to shop 2
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 to shop 3
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop3.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            // Product 2 to shop 4
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop4.asStr,
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
            assertEquals(shop4.asStr, shops[0]["osm_uid"], map.toString())
            assertEquals(shop3.asStr, shops[1]["osm_uid"], map.toString())
            assertEquals(shop2.asStr, shops[2]["osm_uid"], map.toString())
            assertEquals(shop1.asStr, shops[3]["osm_uid"], map.toString())
        }
    }

    @Test
    fun `latest products added to shops request by a normal user`() {
        withPlanteTestApplication {
            val clientToken = register()
            val map = authedGet(clientToken, "/latest_products_added_to_shops_data/?").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
