package vegancheckteam.plante_server

import io.ktor.server.testing.withTestApplication
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register

class ShopRequestsTest {
    @Before
    fun setUp() {
    }

    @Test
    fun `put products to a shop normal scenario`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode1}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/create_update_product/?barcode=${barcode2}" +
                    "&vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode1}&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmId=$shop2").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop1&osmShopsIds=$shop2").jsonMap()
            val results = map["results"] as Map<*, *>
            assertEquals(2, results.size)

            val shop1Barcodes = (results[shop1] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(2, shop1Barcodes.size)
            assertTrue(shop1Barcodes.contains(barcode1))
            assertTrue(shop1Barcodes.contains(barcode2))

            val shop2Products = results[shop2] as List<*>
            val shop2Barcodes = shop2Products.map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shop2Barcodes.size)
            assertTrue(shop2Barcodes.contains(barcode2))

            val shop2Product = shop2Products[0] as Map<*, *>
            assertEquals("positive", shop2Product["vegetarian_status"])
            assertEquals("negative", shop2Product["vegan_status"])
            assertEquals("community", shop2Product["vegetarian_status_source"])
            assertEquals("community", shop2Product["vegan_status_source"])
        }
    }

    @Test
    fun `putting not existing product to shop creates the product`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode}&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            val results = map["results"] as Map<*, *>
            assertEquals(1, results.size)

            val shopBarcodes = (results[shop] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode))
        }
    }

    @Test
    fun `2 consequent putting products into a shop`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            // 0 shops
            transaction {
                assertEquals(0, ShopTable.select { ShopTable.osmId eq shop }.count())
            }

            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode1}&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 shop now
            transaction {
                assertEquals(1, ShopTable.select { ShopTable.osmId eq shop }.count())
            }
            // 1 product at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var results = map["results"] as Map<*, *>
            var shopBarcodes = (results[shop] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))

            map = authedGet(clientToken, "/put_product_to_shop/?barcode=${barcode2}&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 shop still
            transaction {
                assertEquals(1, ShopTable.select { ShopTable.osmId eq shop }.count())
            }
            // 2 products at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            results = map["results"] as Map<*, *>
            shopBarcodes = (results[shop] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(2, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `putting same product into a shop twice`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            // Let's put the product
            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 product at the shop
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var results = map["results"] as Map<*, *>
            var shopBarcodes = (results[shop] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shopBarcodes.size)

            // Oops, I did it again!
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 product at the shop still!
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            results = map["results"] as Map<*, *>
            shopBarcodes = (results[shop] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shopBarcodes.size)
        }
    }

    @Test
    fun `retrieve shops data when not all shops exist`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // Let's put a product
            var map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])

            // 1 item in the map expected since only 1 of the shops exist in DB
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop1&osmShopsIds=$shop2").jsonMap()
            val results = map["results"] as Map<*, *>
            assertEquals(1, results.size)
        }
    }

    @Test
    fun `retrieve shops data when none of the shops exist`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // 0 item in the map expected since none of the shops exist in DB
            val map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop1&osmShopsIds=$shop2").jsonMap()
            val results = map["results"] as Map<*, *> // But still there's a map!
            assertEquals(0, results.size)
        }
    }

    @Test
    fun `product to shop creation time`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            val map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            transaction {
                val shopId = ShopTable.select { ShopTable.osmId eq shop }.first()[ShopTable.id]
                val row = ProductAtShopTable.select { ProductAtShopTable.shopId eq shopId }.first()
                val creationTime = row[ProductAtShopTable.creationTime]
                val now = ZonedDateTime.now().toEpochSecond()
                assertTrue(now - creationTime < 2)
            }
        }
    }
}
