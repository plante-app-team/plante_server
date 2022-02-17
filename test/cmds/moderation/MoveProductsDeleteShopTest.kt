package vegancheckteam.plante_server.cmds.moderation

import io.ktor.server.testing.TestApplicationEngine
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.cmds.CreateShopTestingOsmResponses
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class MoveProductsDeleteShopTest {
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
    fun `move_products_delete_shop very fragile test with REAL osm responses`() {
        withPlanteTestApplication {
            // Create shop
            val user = register()
            var map = authedGet(user, "/create_shop/", mapOf(
                "lat" to "-24",
                "lon" to "44",
                "name" to "myshop",
                "type" to "general",
                "productionOsmDb" to "false",
            )).jsonMap()
            val osmUIDStr = map["osm_uid"] as String
            val osmUID = OsmUID.from(osmUIDStr)
            assertEquals(OsmElementType.NODE, osmUID.elementType)

            // Delete shop
            val moderator = registerModerator()
            map = authedGet(moderator, "/move_products_delete_shop/", mapOf(
                "badOsmUID" to osmUIDStr,
                "goodOsmUID" to OsmUID.from(OsmElementType.NODE, "4450971294").asStr,
                "productionOsmDb" to "false",
            )).jsonMap()
            assertEquals("ok", map["result"])
        }
    }

    @Test
    fun `move_products_delete_shop by normal user`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/move_products_delete_shop/", mapOf(
                "badOsmUID" to OsmUID.from(OsmElementType.NODE, "4450971294").asStr,
                "goodOsmUID" to OsmUID.from(OsmElementType.NODE, "4450971294").asStr,
                "productionOsmDb" to "false",
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `move_products_delete_shop good scenario`() {
        withPlanteTestApplication {
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val (user, userId) = registerAndGetTokenWithID()
            val moderator = registerModerator()

            // Prepare shops
            createShopCmd(user, badShopUID.osmId)
            putProductToShop(user, barcode1, badShopUID)
            putProductToShop(user, barcode2, goodShopUID)
            assertEquals(setOf(barcode1), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode2), getProductsAtShop(user, goodShopUID))
            var goodShop = getShop(user, goodShopUID)!!
            var badShop = getShop(user, badShopUID)!!
            assertEquals(1, goodShop["products_count"])
            assertEquals(1, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(null, badShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(moderator, badShop = badShopUID, goodShop = goodShopUID)

            // Verify consequences
            assertEquals(emptySet(), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode1, barcode2), getProductsAtShop(user, goodShopUID))
            goodShop = getShop(user, goodShopUID)!!
            badShop = getShop(user, badShopUID)!!
            assertEquals(2, goodShop["products_count"])
            assertEquals(0, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(true, badShop["deleted"])

            // User should keep being the author of all related to shops data,
            // the moderator shouldn't be one
            assertTheOnlyShopRelatedDataCreator(userId)
        }
    }

    private fun assertTheOnlyShopRelatedDataCreator(userId: String) {
        transaction {
            var authorsIds = ProductAtShopTable.selectAll().map { it[ProductAtShopTable.creatorUserId] }
            assertTrue(authorsIds.all { it?.toString() == userId })
            authorsIds = ProductPresenceVoteTable.selectAll().map { it[ProductPresenceVoteTable.votedUserId] }
            assertTrue(authorsIds.all { it.toString() == userId })
            authorsIds = ShopTable.selectAll().map { it[ShopTable.creatorUserId] }
            assertTrue(authorsIds.all { it.toString() == userId })
        }
    }

    @Test
    fun `move_products_delete_shop when there is no good osm shop in OSM`() {
        withPlanteTestApplication {
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val user = register()
            val moderator = registerModerator()

            // Prepare shops
            createShopCmd(user, badShopUID.osmId)
            putProductToShop(user, barcode1, badShopUID)
            putProductToShop(user, barcode2, goodShopUID)
            assertEquals(setOf(barcode1), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode2), getProductsAtShop(user, goodShopUID))
            val goodShop = getShop(user, goodShopUID)!!
            val badShop = getShop(user, badShopUID)!!
            assertEquals(1, goodShop["products_count"])
            assertEquals(1, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(null, badShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(
                moderator,
                badShop = badShopUID,
                goodShop = goodShopUID,
                goodShopFoundInOsm = false,
                expectedError = "shop_not_found")
        }
    }

    @Test
    fun `move_products_delete_shop when there is no bad shop in OSM`() {
        withPlanteTestApplication {
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val (user, userId) = registerAndGetTokenWithID()
            val moderator = registerModerator()

            // Prepare shops
            createShopCmd(user, badShopUID.osmId)
            putProductToShop(user, barcode1, badShopUID)
            putProductToShop(user, barcode2, goodShopUID)
            assertEquals(setOf(barcode1), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode2), getProductsAtShop(user, goodShopUID))
            var goodShop = getShop(user, goodShopUID)!!
            var badShop = getShop(user, badShopUID)!!
            assertEquals(1, goodShop["products_count"])
            assertEquals(1, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(null, badShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(
                moderator,
                badShop = badShopUID,
                goodShop = goodShopUID,
                badShopFoundInOsm = false)

            // Verify consequences
            assertEquals(emptySet(), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode1, barcode2), getProductsAtShop(user, goodShopUID))
            goodShop = getShop(user, goodShopUID)!!
            badShop = getShop(user, badShopUID)!!
            assertEquals(2, goodShop["products_count"])
            assertEquals(0, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(true, badShop["deleted"])

            // User should keep being the author of all related to shops data,
            // the moderator shouldn't be one
            assertTheOnlyShopRelatedDataCreator(userId)
        }
    }

    @Test
    fun `move_products_delete_shop when there is no good shop in DB`() {
        withPlanteTestApplication {
            val barcode1 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val (user, userId) = registerAndGetTokenWithID()
            val moderator = registerModerator()

            // Prepare shops
            createShopCmd(user, badShopUID.osmId)
            putProductToShop(user, barcode1, badShopUID)
            assertEquals(setOf(barcode1), getProductsAtShop(user, badShopUID))
            assertEquals(emptySet(), getProductsAtShop(user, goodShopUID))
            var goodShop = getShop(user, goodShopUID)
            var badShop = getShop(user, badShopUID)!!
            assertEquals(null, goodShop)
            assertEquals(1, badShop["products_count"])
            assertEquals(null, badShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(moderator, badShop = badShopUID, goodShop = goodShopUID)

            // Verify consequences
            assertEquals(emptySet(), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode1), getProductsAtShop(user, goodShopUID))
            goodShop = getShop(user, goodShopUID)!!
            badShop = getShop(user, badShopUID)!!
            assertEquals(1, goodShop["products_count"])
            assertEquals(0, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(true, badShop["deleted"])

            // User should keep being the author of all related to shops data,
            // the moderator shouldn't be one
            assertTheOnlyShopRelatedDataCreator(userId)
        }
    }

    @Test
    fun `move_products_delete_shop when there is no bad shop in DB`() {
        withPlanteTestApplication {
            val barcode2 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val user = register()
            val moderator = registerModerator()

            // Prepare shops
            putProductToShop(user, barcode2, goodShopUID)
            assertEquals(setOf(), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode2), getProductsAtShop(user, goodShopUID))
            val goodShop = getShop(user, goodShopUID)!!
            val badShop = getShop(user, badShopUID)
            assertEquals(1, goodShop["products_count"])
            assertEquals(null, badShop)
            assertEquals(null, goodShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(
                moderator,
                badShop = badShopUID,
                goodShop = goodShopUID,
                expectedError = "shop_not_found")
        }
    }

    @Test
    fun `move_products_delete_shop when bad shop is not created by us`() {
        withPlanteTestApplication {
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val badShopUID = generateFakeOsmUID()
            val goodShopUID = generateFakeOsmUID()
            val user = register()
            val moderator = registerModerator()

            // Prepare shops
            // createShopCmd(badShopUID.osmId) // Nope
            putProductToShop(user, barcode1, badShopUID)
            putProductToShop(user, barcode2, goodShopUID)
            assertEquals(setOf(barcode1), getProductsAtShop(user, badShopUID))
            assertEquals(setOf(barcode2), getProductsAtShop(user, goodShopUID))
            val goodShop = getShop(user, goodShopUID)!!
            val badShop = getShop(user, badShopUID)!!
            assertEquals(1, goodShop["products_count"])
            assertEquals(1, badShop["products_count"])
            assertEquals(null, goodShop["deleted"])
            assertEquals(null, badShop["deleted"])

            // Execute cmd
            moveProductsDeleteShopCmd(
                moderator,
                badShop = badShopUID,
                goodShop = goodShopUID,
                expectedError = "wont_delete_shop_not_created_by_us")
        }
    }

    private fun TestApplicationEngine.putProductToShop(user: String, barcode: String, shop: OsmUID) {
        val map = authedGet(user, "/put_product_to_shop/?barcode=$barcode&shopOsmUID=${shop.asStr}").jsonMap()
        assertEquals("ok", map["result"])
    }

    private fun TestApplicationEngine.getProductsAtShop(user: String, shop: OsmUID): Set<String> {
        val map = authedGet(user, "/products_at_shops_data/?osmShopsUIDs=$shop").jsonMap()
        val results = map["results_v2"] as Map<*, *>
        val shopJson = results[shop.asStr] as Map<*, *>?
        if (shopJson == null) {
            return emptySet()
        }
        val products = (shopJson["products"] as List<*>).map { it as Map<*, *> }
        return products.map { it["barcode"] as String }.toSet()
    }

    private fun TestApplicationEngine.getShop(user: String, osmUID: OsmUID): Map<*, *>? {
        val map = authedGet(user, "/shops_data/", body = """ { "osm_uids": [ "${osmUID.asStr}" ] } """).jsonMap()
        val results = map["results_v2"] as Map<*, *>
        return results[osmUID.asStr] as Map<*, *>?
    }

    private fun TestApplicationEngine.createShopCmd(user: String, osmId: String) {
        val fakeOsmResponses = String(
            Base64.getEncoder().encode(
                CreateShopTestingOsmResponses("123456", osmId, "").toString().toByteArray()))
        val map = authedGet(user, "/create_shop/", mapOf(
            "lat" to "-24",
            "lon" to "44",
            "name" to "myshop",
            "type" to "general",
            "testingResponsesJsonBase64" to fakeOsmResponses,
        )).jsonMap()
        assertEquals(osmId, map["osm_id"])
    }

    private fun TestApplicationEngine.moveProductsDeleteShopCmd(
            user: String,
            badShop: OsmUID,
            goodShop: OsmUID,
            expectedError: String? = null,
            badShopFoundInOsm: Boolean = true,
            goodShopFoundInOsm: Boolean = true) {
        val fakeOsmResponses = String(
            Base64.getEncoder().encode(
                MoveProductsDeleteShopTestingOsmResponses(
                    "123456", "", "",
                    goodShopFound = goodShopFoundInOsm,
                    badShopFound = badShopFoundInOsm
                ).toString().toByteArray()))
        val map = authedGet(user, "/move_products_delete_shop/", mapOf(
            "badOsmUID" to badShop.asStr,
            "goodOsmUID" to goodShop.asStr,
            "testingResponsesJsonBase64" to fakeOsmResponses
        )).jsonMap()
        if (expectedError == null) {
            assertEquals("ok", map["result"], map.toString())
        } else {
            assertEquals(expectedError, map["error"], map.toString())
        }
    }
}
