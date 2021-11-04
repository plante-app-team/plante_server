package vegancheckteam.plante_server.cmds

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ProductsPresenceVotingTests {
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
    fun `normal user cannot get product presence votes`() {
        withPlanteTestApplication {
            val clientToken = register()
            val map = authedGet(clientToken, "/product_presence_votes_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `adding product to shop creates a vote for its presence`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            val vote = votes[0] as Map<*, *>
            assertEquals(vote["barcode"], barcode)
            assertEquals(vote["shop_osm_id"], shop.osmId)
            assertEquals(vote["shop_osm_uid"], shop.asStr)
            assertEquals(vote["vote_val"], 1)
        }
    }

    @Test
    fun `voting a product out of a shop when all votes are negative`() {
        withPlanteTestApplication {
            val userWhoPutsProduct = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(userWhoPutsProduct, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            var anotherUser: String
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                    "barcode" to barcode,
                    "shopOsmUID" to shop.asStr,
                    "voteVal" to "0",
                    "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
                assertEquals(false, map["deleted"])
            }

            // The product is expected to still be in the shop
            anotherUser = register()
            map = authedGet(anotherUser, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            assertEquals(true, map["deleted"])

            // The product is expected to be removed from the shop
            map = authedGet(anotherUser, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop when all initial votes are positive`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            // Voting for the product! Go product, u da best!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Voting out!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
                assertEquals(false, map["deleted"])
            }

            // The product is expected still to be in the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // One last vote
            val anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            assertEquals(true, map["deleted"])

            // The product is expected to be removed from the shop
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop does not work when sequence of negative votes is broken`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // So many negative votes wow!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 2) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
                assertEquals(false, map["deleted"])
            }

            // One positive vote ftw!
            var anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Another bunch of negative votes!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION - 1) {
                anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
                assertEquals(false, map["deleted"])
            }

            // The product is expected still to be in the shop because of that one positive vote
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            val productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out of a shop does not remove it from other shops`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Both shops expected to have the product
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop1").jsonMap()
            var productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop2").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // Voting out of the first shop
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Now only the second shop is expected to have the product
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop1").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop2").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
        }
    }

    @Test
    fun `voting a product out deletes its votes`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            repeat(2) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // 3 votes expected
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(3, votes.size)

            // Voting out!
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
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
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Both shops expected to have a vote
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Voting out of the first shop
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Only second shop is expected to have a vote
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(0, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop2").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
        }
    }

    @Test
    fun `extra product presence votes are deleted`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            // Voting twice as many times as the max
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION * 2) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting only MIN_NEGATIVES_VOTES_FOR_DELETION votes
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(MIN_NEGATIVES_VOTES_FOR_DELETION, votes.size)
        }
    }

    @Test
    fun `a product can have lots of votes in different shops`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Vote for shop 1
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for shop 2
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting 2 * MIN_NEGATIVES_VOTES_FOR_DELETION votes because of 2 shops
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(2 * MIN_NEGATIVES_VOTES_FOR_DELETION, votes.size)
        }
    }

    @Test
    fun `a shop can have lots of votes for different products`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Vote for product 1
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }
            // Vote for product 2
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION * 3) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Expecting 2 * MIN_NEGATIVES_VOTES_FOR_DELETION votes because of 2 products
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop").jsonMap()
            val votes = map["votes"] as List<*>
            assertEquals(2 * MIN_NEGATIVES_VOTES_FOR_DELETION, votes.size)
        }
    }

    @Test
    fun `voting for a not existing product`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Put product 1
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Vote for product 2
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("product_not_found", map["error"])
        }
    }

    @Test
    fun `voting for a product in not existing shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // Put to shop 1
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Vote for product in shop 2
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("shop_not_found", map["error"])
        }
    }

    @Test
    fun `invalid vote`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Put product
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Invalid vote
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "2")).jsonMap()
            assertEquals("invalid_vote_val", map["error"])
        }
    }

    @Test
    fun `voting against a product does nothing if it is not in a shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // Put product 1 to shop 1
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop1.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Put product 2 to shop 2
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop2.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            // Vote AGAINST product 1 in shop 2
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "0")).jsonMap()
            assertEquals("ok", map["result"])
            // It's kind of "deleted" since it was never present
            // in the shop anyway
            assertEquals(true, map["deleted"])

            // Expecting 1 votes for product 1 in shop 1
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1&barcode=$barcode1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 2 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 0 votes for product 1 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop2&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(0, votes.size)
            // Expecting product 1 TO NOT BE in shop 2
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop2").jsonMap()
            val shopBarcodes = productsOfShop(map["results_v2"] as Map<*, *>, shop2).map { it["barcode"] }
            assertEquals(1, shopBarcodes.size)
            assertFalse(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `voting for a product when it is not in a shop puts it into the shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // Put product 1 to shop 1
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop1.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Put product 2 to shop 2
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop2.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            // Vote FOR product 1 in shop 2
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 1 votes for product 1 in shop 1
            val moderator = registerModerator()
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1&barcode=$barcode1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 2 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Expecting 1 votes for product 1 in shop 2
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop2&barcode=$barcode1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            // Expecting product 1 TO BE in shop 2
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop2").jsonMap()
            val shopBarcodes = productsOfShop(map["results_v2"] as Map<*, *>, shop2).map { it["barcode"] }
            assertEquals(2, shopBarcodes.size)
            assertTrue(shopBarcodes.contains(barcode1))
            assertTrue(shopBarcodes.contains(barcode2))
        }
    }

    @Test
    fun `products at shop latest seen time`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val barcode3 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var now = 123

            // Put all products to shop 1
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            val product1ExpectedLastSeenTime = now
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode3,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Vote for product 2 presence a couple of times
            var anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            val product2ExpectedLastSeenTime = now

            // Vote for product 3 presence and then against of it
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode3,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            val product3ExpectedLastSeenTime = now
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode3,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Try to mess with all times by adding the products into another shop and voting for them
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode3,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            anotherUser = register()
            map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                "barcode" to barcode3,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Now verify proper times
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop1").jsonMap()
            val results = map["results_v2"] as Map<*, *>
            assertEquals(1, results.size)
            val shop1Result = results[shop1.asStr] as Map<*, *>

            val productsLastSeen = shop1Result["products_last_seen_utc"] as Map<*, *>
            assertEquals(3, productsLastSeen.size)
            assertEquals(product1ExpectedLastSeenTime, productsLastSeen[barcode1])
            assertEquals(product2ExpectedLastSeenTime, productsLastSeen[barcode2])
            assertEquals(product3ExpectedLastSeenTime, productsLastSeen[barcode3])
        }
    }

    @Test
    fun `shops data after adding products to shops and then voting one out`() {
        withPlanteTestApplication {
            val user = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            // Create products
            var map = authedGet(user, "/create_update_product/?barcode=${barcode1}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/create_update_product/?barcode=${barcode2}" +
                    "&veganStatus=possible").jsonMap()
            assertEquals("ok", map["result"])

            var now = 123

            // Add products
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop1.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop2.asStr,
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify products are added
            val shopsDataRequestBody = """ { "osm_uids": [ "$shop1", "$shop2" ] } """
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            var results = map["results_v2"] as Map<*, *>
            assertEquals(2, results.size)

            var shop1Data = results[shop1.asStr]!! as Map<*, *>
            assertEquals(shop1.osmId, shop1Data["osm_id"])
            assertEquals(shop1.asStr, shop1Data["osm_uid"])
            assertEquals(2, shop1Data["products_count"])

            var shop2Data = results[shop2.asStr]!! as Map<*, *>
            assertEquals(shop2.osmId, shop2Data["osm_id"])
            assertEquals(shop2.asStr, shop2Data["osm_uid"])
            assertEquals(1, shop2Data["products_count"])

            // Vote one out
            repeat(MIN_NEGATIVES_VOTES_FOR_DELETION) {
                val anotherUser = register()
                map = authedGet(anotherUser, "/product_presence_vote/", mapOf(
                    "barcode" to barcode1,
                    "shopOsmUID" to shop1.asStr,
                    "voteVal" to "0",
                    "testingNow" to "${++now}")).jsonMap()
                assertEquals("ok", map["result"])
            }

            // Verify one product is removed from one of the shops
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            results = map["results_v2"] as Map<*, *>
            assertEquals(2, results.size)

            shop1Data = results[shop1.asStr]!! as Map<*, *>
            assertEquals(shop1.osmId, shop1Data["osm_id"])
            assertEquals(shop1.asStr, shop1Data["osm_uid"])
            assertEquals(1, shop1Data["products_count"])

            shop2Data = results[shop2.asStr]!! as Map<*, *>
            assertEquals(shop2.osmId, shop2Data["osm_id"])
            assertEquals(shop2.asStr, shop2Data["osm_uid"])
            assertEquals(1, shop2Data["products_count"])
        }
    }

    @Test
    fun `a user cannot add more than 1 vote`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // 1 vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("ok", map["result"])

            // 1 vote expected still
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
        }
    }

    @Test
    fun `a user can change their vote`() {
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // Positive vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
            var aVote = votes[0] as Map<*, *>
            assertEquals(1, aVote["vote_val"])
            val voteTime1 = aVote["vote_time"] as Int

            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Negative vote expected
            map = authedGet(moderator, "/product_presence_votes_data/?barcode=$barcode").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
            aVote = votes[0] as Map<*, *>
            assertEquals(0, aVote["vote_val"])

            // Time of the vote is expected to be greater
            val voteTime2 = aVote["vote_time"] as Int
            assertTrue(voteTime1 < voteTime2)
        }
    }

    @Test
    fun `a user CAN add more than 1 vote when votes for different products`() {
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop.asStr)).jsonMap()
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
            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1")).jsonMap()
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
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            var map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // 1 vote expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            var votes = map["votes"] as List<*>
            assertEquals(1, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(1, votes.size)

            // Vote!
            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop1.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop2.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("ok", map["result"])

            // 2 votes expected everywhere
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
            map = authedGet(moderator, "/product_presence_votes_data/?shopOsmUID=$shop1").jsonMap()
            votes = map["votes"] as List<*>
            assertEquals(2, votes.size)
        }
    }

    @Test
    fun `user who added a product to a shop can then remove it by a single vote`() {
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // Let's have another user vote for its presence
            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // The product is expected to still be in the shop
            map = authedGet(user1, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            // And then the original user votes
            map = authedGet(user1, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            assertEquals(true, map["deleted"])

            // The product is expected to be removed from the shop
            map = authedGet(user1, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(0, productsAtShop.size)
        }
    }

    @Test
    fun `user who DID NOT add a product to a shop CANNOT remove it by a single vote`() {
        withPlanteTestApplication {
            val user1 = register()
            val user2 = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var now = 123
            var map = authedGet(user1, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])

            // The product is expected to still be in the shop
            map = authedGet(user2, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            var productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)

            map = authedGet(user2, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0",
                "testingNow" to (++now).toString())).jsonMap()
            assertEquals("ok", map["result"])
            assertEquals(false, map["deleted"])

            // The product is expected to still be in the shop
            map = authedGet(user2, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
            productsAtShop = map["results_v2"] as Map<*, *>
            assertEquals(1, productsAtShop.size)
        }
    }
}

private fun productsOfShop(shops: Map<*, *>, shopOsmUID: OsmUID): List<Map<*, *>> {
    val shop = shops[shopOsmUID.asStr] as Map<*, *>
    val products = shop["products"] as List<*>
    return products.map { it as Map<*, *> }
}
