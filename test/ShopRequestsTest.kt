package vegancheckteam.plante_server

import io.ktor.server.testing.withTestApplication
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
import vegancheckteam.plante_server.cmds.CreateShopTestingOsmResponses
import vegancheckteam.plante_server.cmds.MAX_CREATED_SHOPS_IN_SEQUENCE
import vegancheckteam.plante_server.cmds.MAX_PRODUCT_PRESENCE_VOTES_COUNT
import vegancheckteam.plante_server.cmds.MIN_NEGATIVES_VOTES_FOR_DELETION
import vegancheckteam.plante_server.cmds.SHOPS_CREATION_SEQUENCE_LENGTH_SECS
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator

class ShopRequestsTest {
    @Before
    fun setUp() {
        withTestApplication({ module(testing = true) }) {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopTable.deleteAll()
            }
        }
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

            val shop1Barcodes = productsOfShop(results, shop1).map { it["barcode"] }
            assertEquals(2, shop1Barcodes.size)
            assertTrue(shop1Barcodes.contains(barcode1))
            assertTrue(shop1Barcodes.contains(barcode2))

            val shop2Products = productsOfShop(results, shop2)
            val shop2Barcodes = shop2Products.map { it["barcode"] }
            assertEquals(1, shop2Barcodes.size)
            assertTrue(shop2Barcodes.contains(barcode2))

            val shop2Product = shop2Products[0]
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

            val shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
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
            var shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
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
            shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
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
            var shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)

            // Oops, I did it again!
            map = authedGet(clientToken, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            // 1 product at the shop still!
            map = authedGet(clientToken, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            results = map["results"] as Map<*, *>
            shopBarcodes = productsOfShop(results, shop).map { it["barcode"] }
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

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT - 2) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // The product is expected to still be in the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            val anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // Voting out!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // The product is expected still to be in the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop").jsonMap()
            var productsAtShop = map["results"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // One last vote
            val anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // One positive vote ftw!
            var anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Another bunch of negative votes!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }

            // 3 votes expected
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(3, votes.size)

            // Voting out!
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=0&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for shop 2
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop2&voteVal=1&testingNow=${++now}").jsonMap()
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
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for product 2
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop&voteVal=1&testingNow=${++now}").jsonMap()
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
            val shopBarcodes = productsOfShop(map["results"] as Map<*, *>, shop2).map { it["barcode"] }
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
            val shopBarcodes = productsOfShop(map["results"] as Map<*, *>, shop2).map { it["barcode"] }
            assertEquals(2, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `products at shop latest seen time`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val barcode3 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var now = 123

            // Put all products to shop 1
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            val product1ExpectedLastSeenTime = now
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode3&shopOsmId=$shop1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Vote for product 2 presence a couple of times
            var anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop1&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop1&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            val product2ExpectedLastSeenTime = now

            // Vote for product 3 presence and then against of it
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode3&shopOsmId=$shop1&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            val product3ExpectedLastSeenTime = now
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode3&shopOsmId=$shop1&voteVal=0&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Try to mess with all times by adding the products into another shop and voting for them
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/?barcode=$barcode3&shopOsmId=$shop2&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop2&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop2&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/?barcode=$barcode3&shopOsmId=$shop2&voteVal=1&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Now verify proper times
            map = authedGet(user, "/products_at_shops_data/?osmShopsIds=$shop1").jsonMap()
            val results = map["results"] as Map<*, *>
            assertEquals(1, results.size)
            val shop1Result = results[shop1] as Map<*, *>

            val productsLastSeen = shop1Result["products_last_seen_utc"] as Map<*, *>
            assertEquals(3, productsLastSeen.size)
            assertEquals(product1ExpectedLastSeenTime, productsLastSeen[barcode1])
            assertEquals(product2ExpectedLastSeenTime, productsLastSeen[barcode2])
            assertEquals(product3ExpectedLastSeenTime, productsLastSeen[barcode3])
        }
    }

    @Test
    fun `create shop with fake osm responses`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val map = authedGet(user, "/create_shop/?lat=-24&lon=44&name=myshop&type=general&productionDb=false").jsonMap()
            val osmId = map["osm_id"] as String
            assertTrue(0 < osmId.toLong() && osmId.toLong() < Long.MAX_VALUE)
        }
    }

    @Test
    fun `shop creation creates a moderator task`() {
        withTestApplication({ module(testing = true) }) {
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
            assertEquals("654322", task["osm_id"])
            assertEquals("osm_shop_creation", task["task_type"])
        }
    }

    @Test
    fun `shop creation DOES NOT create a moderator task when not-prod osm db is used`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val user = register()

            var now = 123
            for (index in 0 until MAX_CREATED_SHOPS_IN_SEQUENCE) {
                val osmId = "65434$index"
                val fakeOsmResponses = String(
                    Base64.getEncoder().encode(
                        CreateShopTestingOsmResponses("123456", osmId, "").toString().toByteArray()))
                val map = authedGet(
                    user, "/create_shop/", mapOf(
                        "testingNow" to "$now",
                        "lat" to "-24",
                        "lon" to "44",
                        "name" to "myshop",
                        "type" to "general",
                        "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
                assertEquals(osmId, map["osm_id"])
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
        withTestApplication({ module(testing = true) }) {
            val user = register()

            // Create too many shops
            val now = 123
            for (index in 0 until MAX_CREATED_SHOPS_IN_SEQUENCE * 2) {
                val osmId = "65436$index"
                val fakeOsmResponses = String(
                    Base64.getEncoder().encode(
                        CreateShopTestingOsmResponses("123456", osmId, "").toString().toByteArray()))
                val map = authedGet(
                    user, "/create_shop/", mapOf(
                        "testingNow" to "$now",
                        "lat" to "-24",
                        "lon" to "44",
                        "name" to "myshop",
                        "type" to "general",
                        "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
                if (index < MAX_CREATED_SHOPS_IN_SEQUENCE) {
                    assertEquals(osmId, map["osm_id"])
                } else {
                    assertEquals("max_shops_created_for_now", map["error"])
                }
            }

            // But another shop still can be created when a request to osm is not needed
            val barcode = UUID.randomUUID()
            val osmId = "65437"

            var newShopExists = transaction {
                !ShopTable.select { ShopTable.osmId eq osmId }.empty()
            }
            assertFalse(newShopExists)

            val map = authedGet(user, "/put_product_to_shop/", mapOf(
                "testingNow" to "$now",
                "barcode" to "$barcode",
                "shopOsmId" to osmId)).jsonMap()
            assertEquals("ok", map["result"])

            newShopExists = transaction {
                !ShopTable.select { ShopTable.osmId eq osmId }.empty()
            }
            assertTrue(newShopExists)
        }
    }

    @Test
    fun `shops data after shop creation`() {
        withTestApplication({ module(testing = true) }) {
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
    fun `shops data after adding products to shops and then voting one out`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            // Create products
            var map = authedGet(user, "/create_update_product/?barcode=${barcode1}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/create_update_product/?barcode=${barcode2}" +
                    "&vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            var now = 123;

            // Add products
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmId" to shop1,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop1,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmId" to shop2,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify products are added
            val shopsDataRequestBody = """ { "osm_ids": [ "$shop1", "$shop2" ] } """
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            var results = map["results"] as Map<*, *>
            assertEquals(2, results.size)

            var shop1Data = results[shop1]!! as Map<*, *>
            assertEquals(shop1, shop1Data["osm_id"])
            assertEquals(2, shop1Data["products_count"])

            var shop2Data = results[shop2]!! as Map<*, *>
            assertEquals(shop2, shop2Data["osm_id"])
            assertEquals(1, shop2Data["products_count"])

            // Vote one out
            repeat(MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                    "barcode" to barcode1,
                    "shopOsmId" to shop1,
                    "voteVal" to "0",
                    "testingNow" to "${++now}")).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Verify one product is removed from one of the shops
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            results = map["results"] as Map<*, *>
            assertEquals(2, results.size)

            shop1Data = results[shop1]!! as Map<*, *>
            assertEquals(shop1, shop1Data["osm_id"])
            assertEquals(1, shop1Data["products_count"])

            shop2Data = results[shop2]!! as Map<*, *>
            assertEquals(shop2, shop2Data["osm_id"])
            assertEquals(1, shop2Data["products_count"])
        }
    }

    @Test
    fun `a user cannot add more than 1 vote`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // 1 vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])

            // 1 vote expected still
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
        }
    }

    @Test
    fun `a user can change their vote`() {
        withTestApplication({ module(testing = true) }) {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // Positive vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            var aVote = votes[0] as Map<*, *>
            assertEquals(1, aVote["vote_val"])
            val voteTime1 = aVote["vote_time"] as Int

            map = authedGet(user, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop&voteVal=0&testingNow=${++now}").jsonMap()
            assertEquals("ok", map["result"])

            // Negative vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            aVote = votes[0] as Map<*, *>
            assertEquals(0, aVote["vote_val"])

            // Time of the vote is expected to be greater
            val voteTime2 = aVote["vote_time"] as Int
            assertTrue(voteTime1 < voteTime2);
        }
    }

    @Test
    fun `a user CAN add more than 1 vote when votes for different products`() {
        withTestApplication({ module(testing = true) }) {
            val user1 = register()
            val user2 = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()

            var map = authedGet(user1, "/put_product_to_shop/?barcode=$barcode1&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/put_product_to_shop/?barcode=$barcode2&shopOsmId=$shop").jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // 1 vote expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Vote!
            map = authedGet(user2, "/product_presence_vote/?barcode=$barcode1&shopOsmId=$shop&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user2, "/product_presence_vote/?barcode=$barcode2&shopOsmId=$shop&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])

            // 2 votes expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
        }
    }

    @Test
    fun `a user CAN add more than 1 vote when votes for same product in different shops`() {
        withTestApplication({ module(testing = true) }) {
            val user1 = register()
            val user2 = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = UUID.randomUUID().toString()
            val shop2 = UUID.randomUUID().toString()

            var map = authedGet(user1, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/put_product_to_shop/?barcode=$barcode&shopOsmId=$shop2").jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // 1 vote expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Vote!
            map = authedGet(user2, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop1&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user2, "/product_presence_vote/?barcode=$barcode&shopOsmId=$shop2&voteVal=1").jsonMap()
            assertEquals("ok", map["result"])

            // 2 votes expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmId=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
        }
    }
}

private fun productsOfShop(shops: Map<*, *>, shopOsmId: String): List<Map<*, *>> {
    val shop = shops[shopOsmId] as Map<*, *>
    val products = shop["products"] as List<*>
    return products.map { it as Map<*, *> }
}
