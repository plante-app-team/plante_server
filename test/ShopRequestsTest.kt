package vegancheckteam.plante_server

import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.cmds.CreateShopTestingOsmResponses
import vegancheckteam.plante_server.cmds.MAX_CREATED_SHOPS_IN_SEQUENCE
import vegancheckteam.plante_server.cmds.SHOPS_CREATION_SEQUENCE_LENGTH_SECS
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ShopRequestsTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
            }
        }
    }

    @Test
    fun `put products to a shop normal scenario`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode1}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/create_update_product/?barcode=${barcode2}" +
                    "&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode1}&shopOsmUID=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmUID=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmUID=$shop2").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop1&osmShopsUIDs=$shop2").jsonMap()
            val results = map["results_v2"] as Map<*, *>
            assertEquals(2, results.size)

            val shop1Barcodes = productsOfShop(results, shop1).map { it["barcode"] }
            assertEquals(2, shop1Barcodes.size)
            assertTrue(shop1Barcodes.contains(barcode1))
            assertTrue(shop1Barcodes.contains(barcode2))

            val shop2Products = productsOfShop(results, shop2)
            val shop2Barcodes = shop2Products.map { it["barcode"] }
            assertEquals(1, shop2Barcodes.size)
            assertTrue(shop2Barcodes.contains(barcode2))

            val shop2Product = shop2Products[0]
            assertEquals("negative", shop2Product["vegan_status"])
            assertEquals("community", shop2Product["vegan_status_source"])
        }
    }

    @Test
    fun `products without veg-status are counted as a part of shop's product range`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])
            // Verify it doesn't have a vegan status
            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(null, map["vegan_status"])
            assertEquals(null, map["vegan_status_source"])

            // Put product to the shop
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode}&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // Ensure the product is counted in the "shops_data" response
            map = authedGet(clientToken, "/shops_data/", body = """ { "osm_uids": [ "$shop" ] } """).jsonMap()
            var results = map["results_v2"] as Map<*, *>
            assertEquals(1, results.size)
            val shopData = results[shop.asStr]!! as Map<*, *>
            assertEquals(1, shopData["products_count"])

            // Ensure the product is counted in the "products_at_shops_data" response
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            results = map["results_v2"] as Map<*, *>
            assertEquals(1, results.size)
            val productsAtShop = productsOfShop(results, shop)
            assertEquals(listOf(barcode), productsAtShop.map { it["barcode"] })
            // The product has no vegan status
            val productAtShop = productsAtShop.first()
            assertEquals(null, productAtShop["vegan_status"])
            assertEquals(null, productAtShop["vegan_status_source"])
        }
    }

    @Test
    fun `putting not existing product to shop creates the product`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode}&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])

            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            val results = map["results_v2"] as Map<*, *>
            assertEquals(1, results.size)

            val shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode))
        }
    }

    @Test
    fun `2 consequent putting products into a shop`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // 0 shops
            transaction {
                assertEquals(0, ShopTable.select { ShopTable.osmUID eq shop.asStr }.count())
            }

            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode1}&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 shop now
            transaction {
                assertEquals(1, ShopTable.select { ShopTable.osmUID eq shop.asStr }.count())
            }
            // 1 product at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var results = map["results_v2"] as Map<*, *>
            var shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 shop still
            transaction {
                assertEquals(1, ShopTable.select { ShopTable.osmUID eq shop.asStr }.count())
            }
            // 2 products at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            results = map["results_v2"] as Map<*, *>
            shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(2, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `putting same product into a shop twice`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Let's put the product
            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 product at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var results = map["results_v2"] as Map<*, *>
            var shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)

            // Oops, I did it again!
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 product at the shop still!
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            results = map["results_v2"] as Map<*, *>
            shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)
        }
    }

    @Test
    fun `retrieve shops data when not all shops exist`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // Let's put a product
            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmUID=$shop1").jsonMap()
            assertEquals("ok", map["result"])

            // 1 item in the map expected since only 1 of the shops exist in DB
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop1&osmShopsUIDs=$shop2").jsonMap()
            val results = map["results_v2"] as Map<*, *>
            assertEquals(1, results.size)
        }
    }

    @Test
    fun `retrieve shops data when none of the shops exist`() {
        withPlanteTestApplication {
            val clientToken = register()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // 0 item in the map expected since none of the shops exist in DB
            val map = authedGet(clientToken, "/products_at_shops_data/?osmShopsUIDs=$shop1&osmShopsUIDs=$shop2").jsonMap()
            val results = map["results_v2"] as Map<*, *> // But still there's a map!
            assertEquals(0, results.size)
        }
    }

    @Test
    fun `product to shop creation time`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            val map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmUID=$shop").jsonMap()
            assertEquals("ok", map["result"])

            transaction {
                val shopId = ShopTable.select { ShopTable.osmUID eq shop.asStr }.first()[ShopTable.id]
                val row = ProductAtShopTable.select { ProductAtShopTable.shopId eq shopId }.first()
                val creationTime = row[ProductAtShopTable.creationTime]
                val now = ZonedDateTime.now().toEpochSecond()
                assertTrue(now - creationTime < 2)
            }
        }
    }

    @Test
    fun `create shop with fake osm responses`() {
        withPlanteTestApplication {
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654321", "").toString().toByteArray()))
            val user = register()
            val map = authedGet(user, "/create_shop/?lat=-24&lon=44&name=myshop&type=general&testingResponsesJsonBase64=$fakeOsmResponses").jsonMap()
            assertEquals("654321", map["osm_id"])
        }
    }

    @Test
    fun `create shop with invalid shop type`() {
        withPlanteTestApplication {
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("you", "are", "boldone").toString().toByteArray()))
            val user = register()
            val map = authedGet(user, "/create_shop/?lat=-24&lon=44&name=myshop&type=generalkenobi&testingResponsesJsonBase64=$fakeOsmResponses").jsonMap()
            assertEquals("invalid_shop_type", map["error"])
        }
    }

    @Test
    fun `a very fragile test of create_shop with REAL osm responses`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/create_shop/?lat=-24&lon=44&name=myshop&type=general&productionDb=false").jsonMap()
            val osmId = map["osm_id"] as String
            val osmUIDStr = map["osm_uid"] as String
            val osmUID = OsmUID.from(osmUIDStr)
            assertTrue(0 < osmId.toLong() && osmId.toLong() < Long.MAX_VALUE)
            assertEquals(osmId, osmUID.osmId)
            assertEquals(OsmElementType.NODE, osmUID.elementType)
        }
    }

    @Test
    fun `shop creation creates a moderator task`() {
        withPlanteTestApplication {
            val user = register()
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654322", "").toString().toByteArray()))

            // No moderator tasks yet
            var map = authedGet(moderator, "/assign_moderator_task/?assignee=${moderatorId}").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
            map = authedGet(moderator, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals(emptyList<Any>(), map["tasks"])

            map = authedGet(user, "/create_shop/", mapOf(
                    "lat" to "-24",
                    "lon" to "44",
                    "name" to "myshop",
                    "type" to "general",
                    "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("654322", map["osm_id"])

            // A moderator task appeared
            map = authedGet(moderator, "/assign_moderator_task/?assignee=${moderatorId}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(moderator, "/assigned_moderator_tasks_data/").jsonMap()
            val tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            val task = tasks[0] as Map<*, *>
            assertEquals("1:654322", task["osm_uid"])
            assertEquals("osm_shop_creation", task["task_type"])
        }
    }

    @Test
    fun `shop creation DOES NOT create a moderator task when not-prod osm db is used`() {
        withPlanteTestApplication {
            val user = register()
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654323", "").toString().toByteArray()))

            // No moderator tasks yet
            var map = authedGet(moderator, "/assign_moderator_task/?assignee=${moderatorId}").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
            map = authedGet(moderator, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals(emptyList<Any>(), map["tasks"])

            map = authedGet(user, "/create_shop/", mapOf(
                "lat" to "-24",
                "lon" to "44",
                "name" to "myshop",
                "type" to "general",
                "productionDb" to "false",
                "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("654323", map["osm_id"])

            // A moderator still not appeared
            map = authedGet(moderator, "/assign_moderator_task/?assignee=${moderatorId}").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
            map = authedGet(moderator, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals(emptyList<Any>(), map["tasks"])
        }
    }

    @Test
    fun `shops creation sequence max`() {
        withPlanteTestApplication {
            val user = register()

            var now = 123
            for (index in 0 until MAX_CREATED_SHOPS_IN_SEQUENCE) {
                val osmUID = generateFakeOsmUID(index)
                val fakeOsmResponses = String(
                    Base64.getEncoder().encode(
                        CreateShopTestingOsmResponses("123456", osmUID.osmId, "").toString().toByteArray()))
                val map = authedGet(
                    user, "/create_shop/", mapOf(
                        "testingNow" to "$now",
                        "lat" to "-24",
                        "lon" to "44",
                        "name" to "myshop",
                        "type" to "general",
                        "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
                assertEquals(osmUID.osmId, map["osm_id"])
            }
            // Trying to create another shop over the sequence max
            var fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "65435", "").toString().toByteArray()))
            var map = authedGet(
                user, "/create_shop/", mapOf(
                    "testingNow" to "$now",
                    "lat" to "-24",
                    "lon" to "44",
                    "name" to "myshop",
                    "type" to "general",
                    "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("max_shops_created_for_now", map["error"])

            // Trying to create another shop over the sequence max AFTER timeout has passed
            now += SHOPS_CREATION_SEQUENCE_LENGTH_SECS + 1
            fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "65435", "").toString().toByteArray()))
            map = authedGet(
                user, "/create_shop/", mapOf(
                    "testingNow" to "$now",
                    "lat" to "-24",
                    "lon" to "44",
                    "name" to "myshop",
                    "type" to "general",
                    "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("65435", map["osm_id"])
        }
    }

    @Test
    fun `when shop creation sequence max is reached existing osm shops can be added to db anyways`() {
        withPlanteTestApplication {
            val user = register()

            // Create too many shops
            val now = 123
            for (index in 0 until MAX_CREATED_SHOPS_IN_SEQUENCE * 2) {
                val osmUID = generateFakeOsmUID(index)
                val fakeOsmResponses = String(
                    Base64.getEncoder().encode(
                        CreateShopTestingOsmResponses("123456", osmUID.osmId, "").toString().toByteArray()))
                val map = authedGet(
                    user, "/create_shop/", mapOf(
                        "testingNow" to "$now",
                        "lat" to "-24",
                        "lon" to "44",
                        "name" to "myshop",
                        "type" to "general",
                        "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
                if (index < MAX_CREATED_SHOPS_IN_SEQUENCE) {
                    assertEquals(osmUID.osmId, map["osm_id"])
                } else {
                    assertEquals("max_shops_created_for_now", map["error"])
                }
            }

            // But another shop still can be created when a request to osm is not needed
            val barcode = UUID.randomUUID()
            val osmUID = generateFakeOsmUID()

            var newShopExists = transaction {
                !ShopTable.select { ShopTable.osmUID eq osmUID.asStr }.empty()
            }
            assertFalse(newShopExists)

            val map = authedGet(user, "/put_product_to_shop/", mapOf(
                "testingNow" to "$now",
                "barcode" to "$barcode",
                "shopOsmUID" to osmUID.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            newShopExists = transaction {
                !ShopTable.select { ShopTable.osmUID eq osmUID.asStr }.empty()
            }
            assertTrue(newShopExists)
        }
    }

    @Test
    fun `shops data after shop creation`() {
        withPlanteTestApplication {
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654321", "").toString().toByteArray()))
            val user = register()
            var map = authedGet(user, "/create_shop/", mapOf(
                "lat" to "-24",
                "lon" to "44",
                "name" to "myshop",
                "type" to "general",
                "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("654321", map["osm_id"])

            val shopsDataRequestBody = """ { "osm_ids": [ "654321", "654322" ] } """
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val results = map["results"] as Map<*, *>
            assertEquals(1, results.size)

            val shopData = results["654321"]!! as Map<*, *>
            assertEquals("654321", shopData["osm_id"])
            assertEquals(0, shopData["products_count"])
        }
    }

    @Test
    fun `shops data does not include non-vegan products count`() {
        withPlanteTestApplication {
            val user = register()
            val shop = generateFakeOsmUID()

            val putProductAndCheckShopsCount = { barcode: String, vegStatus: VegStatus, expectedCount: Int ->
                var map = authedGet(user, "/create_update_product/?barcode=${barcode}&veganStatus=${vegStatus.statusName}").jsonMap()
                assertEquals("ok", map["result"])
                map = authedGet(user, "/put_product_to_shop/", mapOf(
                    "barcode" to barcode,
                    "shopOsmUID" to shop.asStr)).jsonMap()
                assertEquals("ok", map["result"])
                val shopsDataRequestBody = """ { "osm_uids": [ "$shop" ] } """
                map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
                val results = map["results_v2"] as Map<*, *>
                val shopData = results[shop.asStr]!! as Map<*, *>
                assertEquals(expectedCount, shopData["products_count"], "$vegStatus $expectedCount")
            }

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val barcode3 = UUID.randomUUID().toString()
            val barcode4 = UUID.randomUUID().toString()

            // 1
            putProductAndCheckShopsCount(barcode1, VegStatus.POSITIVE, 1)
            // Still 1
            putProductAndCheckShopsCount(barcode2, VegStatus.NEGATIVE, 1)
            // 2
            putProductAndCheckShopsCount(barcode3, VegStatus.POSSIBLE, 2)
            // 3
            putProductAndCheckShopsCount(barcode4, VegStatus.UNKNOWN, 3)

            // All 4 products are in the shop, even though the product
            // with negative status is not counted
            val map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            val results = map["results_v2"] as Map<*, *>
            val shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(setOf(barcode1, barcode2, barcode3, barcode4), shopBarcodes.toSet())
        }
    }
}

private fun productsOfShop(shops: Map<*, *>, shopOsmUID: OsmUID): List<Map<*, *>> {
    val shop = shops[shopOsmUID.asStr] as Map<*, *>
    val products = shop["products"] as List<*>
    return products.map { it as Map<*, *> }
}
