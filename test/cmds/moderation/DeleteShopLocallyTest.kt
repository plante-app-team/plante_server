package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class DeleteShopLocallyTest {
    @Test
    fun `can delete shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shopUid = generateFakeOsmUID()

            // Add a product
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shopUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            // Verify shop is stored
            val shopsDataRequestBody = """ { "osm_uids": [ "$shopUid" ] } """
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            var shop = shopFrom(map, shopUid)
            assertEquals(shopUid.asStr, shop!!["osm_uid"])
            assertEquals(null, shop["deleted"])

            val moderator = registerModerator()
            // Now let's delete the shop successfully
            map = authedGet(moderator, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shopUid.asStr)).jsonMap()
            assertEquals("ok", map["result"], message = map.toString())

            // Verify shop is marked as deleted
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            shop = shopFrom(map, shopUid)
            assertEquals(shopUid.asStr, shop!!["osm_uid"])
            assertEquals(true, shop["deleted"])

            // Verify the product is still at the shop, even though
            // the shop is marked as deleted - we want to easily un-delete the
            // shop if needed.
            // Sorry for messy code.
            assertEquals(1, shop["products_count"])
            map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shopUid").jsonMap()
            val productsAtShop = (map["results_v2"] as Map<*, *>)[shopUid.asStr] as Map<*, *>
            assertEquals(1, (productsAtShop["products"] as List<*>).size)
        }
    }

    @Test
    fun `cannot delete shop by normal user`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shopUid = generateFakeOsmUID()

            // Add a product
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shopUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            // Verify a normal user cannot delete a shop
            map = authedGet(user, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shopUid.asStr)).jsonMap()
            assertEquals("denied", map["error"])

            // Verify the shop is not deleted
            val shopsDataRequestBody = """ { "osm_uids": [ "$shopUid" ] } """
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shop = shopFrom(map, shopUid)
            assertEquals(shopUid.asStr, shop!!["osm_uid"])
        }
    }

    @Test
    fun `voting for a product presence in a deleted shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Put a product to a shop, thus creating the shop
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Remove the product from the shop
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0")).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // Let's delete the shop
            map = authedGet(moderator, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"], message = map.toString())

            // And let's try to vote for the product
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "1")).jsonMap()
            assertEquals("shop_deleted", map["error"])
            // The vote against the product works though (because there is no shop anymore)
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0")).jsonMap()
            assertEquals("ok", map["result"])
            assertEquals(true, map["deleted"])
        }
    }

    @Test
    fun `putting product to a deleted shop`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // Put a product to a shop, thus creating the shop
            var map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            // Remove the product from the shop
            map = authedGet(user, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0")).jsonMap()
            assertEquals("ok", map["result"])

            val moderator = registerModerator()
            // Let's delete the shop
            map = authedGet(moderator, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"], message = map.toString())

            // Now let's try to put the product to the shop again
            map = authedGet(user, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("shop_deleted", map["error"])
        }
    }

    private fun shopFrom(map: Map<*, *>, shopOsmUid: OsmUID): Map<*, *>? {
        val shops = map["results_v2"] as Map<*, *>
        return shops[shopOsmUid.asStr] as Map<*, *>?
    }
}