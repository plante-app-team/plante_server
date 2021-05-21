package vegancheckteam.plante_server

import io.ktor.server.testing.withTestApplication
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.cmds.MAX_PRODUCT_PRESENCE_VOTES_COUNT
import vegancheckteam.plante_server.cmds.MIN_NEGATIVES_VOTES_FOR_DELETION
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator

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

    @Test
    fun `normal user cannot get product presence votes`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val map = authedGet(clientToken, "/product_presence_votes_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `adding product to shop creates a vote for its presence`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            val vote = votes[0] as Map<*, *>
            assertEquals(vote["barcode"], barcode)
            assertEquals(vote["shop_osm_id"], shop)
            assertEquals(vote["vote_val"], 1)
        }
    }

    @Test
    fun `voting a product out of a shop when all votes are negative`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT - 2) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // The product is expected to still be in the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // The product is expected to be removed from the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            productsAtShop = map["results"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop when all initial votes are positive`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            // Voting for the product! Go product, u da best!
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Voting out!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // The product is expected still to be in the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // One last vote
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // The product is expected to be removed from the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            productsAtShop = map["results"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop does not work when sequence of negative votes is broken`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // So many negative votes wow!
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT - 2) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // One positive vote ftw!
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Another bunch of negative votes!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // The product is expected still to be in the shop because of that one positive vote
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            val productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop does not remove it from other shops`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Both shops expected to have the product
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop1").jsonMap()
            var productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop2").jsonMap()
            productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // Voting out of the first shop
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Now only the second shop is expected to have the product
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop1").jsonMap()
            productsAtShop = map["results"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop2").jsonMap()
            productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out deletes its votes`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            repeat(2) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // 3 votes expected
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(3, votes.size)

            // Voting out!
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // 0 votes expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(0, votes.size)
        }
    }

    @Test
    fun `voting a product does not delete its votes for other shops`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Both shops expected to have a vote
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Voting out of the first shop
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Only second shop is expected to have a vote
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(0, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
        }
    }

    @Test
    fun `extra product presence votes are deleted`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            // Voting twice as many times as the max
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 2) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting only MAX_PRODUCT_PRESENCE_VOTES_COUNT votes
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(MAX_PRODUCT_PRESENCE_VOTES_COUNT, votes.size)
        }
    }

    @Test
    fun `a product can have lots of votes in different shops`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Vote for shop 1
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for shop 2
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop2&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting 2 * MAX_PRODUCT_PRESENCE_VOTES_COUNT votes because of 2 shops
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(2 * MAX_PRODUCT_PRESENCE_VOTES_COUNT, votes.size)
        }
    }

    @Test
    fun `a shop can have lots of votes for different products`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123;
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Vote for product 1
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for product 2
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                map = authedGet(user, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting 2 * MAX_PRODUCT_PRESENCE_VOTES_COUNT votes because of 2 products
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(2 * MAX_PRODUCT_PRESENCE_VOTES_COUNT, votes.size)
        }
    }

    @Test
    fun `voting for a not existing product`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            // Put product 1
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])
            // Vote for product 2
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop&voteVal=1").jsonMap()
            assertEquals("product_not_found", map["error"])
        }
    }

    @Test
    fun `voting for a product in not existing shop`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // Put to shop 1
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            // Vote for product in shop 2
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop2&voteVal=1").jsonMap()
            assertEquals("shop_not_found", map["error"])
        }
    }

    @Test
    fun `invalid vote`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            // Put product
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])
            // Invalid vote
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=2").jsonMap()
            assertEquals("invalid_vote_val", map["error"])
        }
    }

    @Test
    fun `voting against a product does nothing if it is not in a shop`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // Put product 1 to shop 1
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            // Put product 2 to shop 2
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop2").jsonMap()
            assertEquals("ok", map["result"])

            // Vote AGAINST product 1 in shop 2
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop2&voteVal=0").jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 1 votes for product 1 in shop 1
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1&barcode=$barcode1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 2 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 0 votes for product 1 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop2&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(0, votes.size)
            // Expecting product 1 TO NOT BE in shop 2
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop2").jsonMap()
            val productsAtShop = map["results"] as Map<*, *>
            val shopBarcodes = (productsAtShop[shop2] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertFalse(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `voting for a product when it is not in a shop puts it into the shop`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // Put product 1 to shop 1
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            // Put product 2 to shop 2
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop2").jsonMap()
            assertEquals("ok", map["result"])

            // Vote FOR product 1 in shop 2
            map = authedGet(user, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop2&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 1 votes for product 1 in shop 1
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1&barcode=$barcode1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 2 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 1 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop2&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            // Expecting product 1 TO BE in shop 2
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop2").jsonMap()
            val productsAtShop = map["results"] as Map<*, *>
            val shopBarcodes = (productsAtShop[shop2] as List<*>).map { (it as Map<*, *>)["barcode"] }
            assertEquals(2, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }
}
