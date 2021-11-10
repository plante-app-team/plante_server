package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

            val moderator = registerModerator()
            map = authedGet(moderator, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shopUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            // Verify shop is not stored anymore
            map = authedGet(user, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            shop = shopFrom(map, shopUid)
            assertNull(shop)
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

    private fun shopFrom(map: Map<*, *>, shopOsmUid: OsmUID): Map<*, *>? {
        val shops = map["results_v2"] as Map<*, *>
        return shops[shopOsmUid.asStr] as Map<*, *>?
    }
}