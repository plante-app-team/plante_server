package vegancheckteam.plante_server.workers

import io.ktor.server.testing.TestApplicationCall
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.junit.After
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.cmds.CreateShopTestingOsmResponses
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.ShopValidationReason
import vegancheckteam.plante_server.osm.OsmShop
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ShopsValidationWorkerTest {
    companion object {
        private const val BAD_COORD = 123.4
        private const val VALIDATED_COORD = 100.0
    }
    private val requestedOsmUidsBatches = mutableListOf<List<OsmUID>>()
    private val allRequestedOsmUids: List<OsmUID>
        get() = requestedOsmUidsBatches.flatten()

    @Before
    fun setUp() {
        requestedOsmUidsBatches.clear()
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
            }
            ShopsValidationWorker.waitUntilIdle()
            ShopsValidationWorker.osmForTests = { uids ->
                requestedOsmUidsBatches.add(uids.map { it.osmUID })
                uids.map { OsmShop(it.osmUID, VALIDATED_COORD, VALIDATED_COORD) }.toSet()
            }
        }
    }

    @After
    fun tearDown() {
        withPlanteTestApplication {
            ShopsValidationWorker.osmForTests = null
        }
    }

    @Test
    fun `inserted by put_product_to_shop shops are being validated`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            var map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$BAD_COORD",
                "lon" to "$BAD_COORD")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()

            val shopsDataRequestBody = """ { "osm_uids": [ "${osmUid.asStr}" ] } """
            map = authedGet(clientToken, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shop = shopFrom(map, osmUid)

            assertEquals(osmUid.asStr, shop["osm_uid"])
            assertEquals(VALIDATED_COORD, shop["lat"])
            assertEquals(VALIDATED_COORD, shop["lon"])
        }
    }

    @Test
    fun `on put_product_to_shop cmd existing shops are not validated`() {
        withPlanteTestApplication {
            // Put product 1
            val barcode1 = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            var map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$VALIDATED_COORD",
                "lon" to "$VALIDATED_COORD")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // First putting validates the shop
            assertEquals(1, requestedOsmUidsBatches.size)

            // Put product 2
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$VALIDATED_COORD",
                "lon" to "$VALIDATED_COORD")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // But second putting doesn't validate the shop
            assertEquals(1, requestedOsmUidsBatches.size)
        }
    }

    @Test
    fun `on put_product_to_shop cmd existing shops ARE validated when received by user coords differ too much`() {
        withPlanteTestApplication {
            // Put product 1
            val barcode1 = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            var map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$VALIDATED_COORD",
                "lon" to "$VALIDATED_COORD")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // First putting validates the shop
            assertEquals(1, requestedOsmUidsBatches.size)

            // Put product 2
            val newCoord = VALIDATED_COORD + ShopTable.MIN_ALLOWED_SHOP_MOVE_DISTANCE * 2
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$newCoord",
                "lon" to "$newCoord")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // Second putting ALSO validates the shop because it moved
            assertEquals(2, requestedOsmUidsBatches.size)
        }
    }

    @Test
    fun `on put_product_to_shop cmd existing shops ARE NOT validated when received by user coords differ only a little`() {
        withPlanteTestApplication {
            // Put product 1
            val barcode1 = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            var map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode1,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$VALIDATED_COORD",
                "lon" to "$VALIDATED_COORD")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // First putting validates the shop
            assertEquals(1, requestedOsmUidsBatches.size)

            // Put product 2
            val newCoord = VALIDATED_COORD + ShopTable.MIN_ALLOWED_SHOP_MOVE_DISTANCE / 2
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode2,
                "shopOsmUID" to osmUid.asStr,
                "lat" to "$newCoord",
                "lon" to "$newCoord")).jsonMap()
            assertEquals("ok", map["result"])

            ShopsValidationWorker.waitUntilIdle()
            // Second putting DOES NOT validate the shop because it moved too little
            assertEquals(1, requestedOsmUidsBatches.size)
        }
    }

    @Test
    fun `when backend creates a shop in OSM such a shop is not validated`() {
        withPlanteTestApplication {
            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654321", "").toString().toByteArray()))
            val user = register()
            val map = authedGet(user, "/create_shop/", mapOf(
                "lat" to "$BAD_COORD",
                "lon" to "$BAD_COORD",
                "name" to "myshop",
                "type" to "general",
                "testingResponsesJsonBase64" to fakeOsmResponses,
            )).jsonMap()
            assertEquals("654321", map["osm_id"])

            // We expect shop creation to not cause shop validation - we (server)
            // created the shop, we trust ourselves.
            ShopsValidationWorker.waitUntilIdle()
            assertEquals(0, requestedOsmUidsBatches.size)
        }
    }

    @Test
    fun `on startup validates shops without validation time`() {
        val (userToken, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopOsmUid = OsmUID.from(OsmElementType.NODE, "12345")
        transaction {
            ShopTable.insert {
                it[osmUID] = shopOsmUid.asStr
                it[creationTime] = 100
                it[creatorUserId] = UUID.fromString(userId)
                it[lat] = BAD_COORD
                it[lon] = BAD_COORD
                it[lastAutoValidationTime] = null
            }
        }

        withPlanteTestApplication {
            ShopsValidationWorker.waitUntilIdle()

            val shopsDataRequestBody = """ { "osm_uids": [ "${shopOsmUid.asStr}" ] } """
            val map = authedGet(userToken, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shop = shopFrom(map, shopOsmUid)

            assertEquals(shopOsmUid.asStr, shop["osm_uid"])
            assertEquals(VALIDATED_COORD, shop["lat"])
            assertEquals(VALIDATED_COORD, shop["lon"])
        }
    }

    @Test
    fun `on startup validates shops with validation time but empty coords`() {
        val (userToken, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopOsmUid = OsmUID.from(OsmElementType.NODE, "12345")
        transaction {
            ShopTable.insert {
                it[osmUID] = shopOsmUid.asStr
                it[creationTime] = 100
                it[creatorUserId] = UUID.fromString(userId)
                it[lat] = null
                it[lon] = null
                it[lastAutoValidationTime] = 123
            }
        }

        withPlanteTestApplication {
            ShopsValidationWorker.waitUntilIdle()

            val shopsDataRequestBody = """ { "osm_uids": [ "${shopOsmUid.asStr}" ] } """
            val map = authedGet(userToken, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shop = shopFrom(map, shopOsmUid)

            assertEquals(shopOsmUid.asStr, shop["osm_uid"])
            assertEquals(VALIDATED_COORD, shop["lat"])
            assertEquals(VALIDATED_COORD, shop["lon"])
        }
    }

    @Test
    fun `shop validation sets last validation time`() {
        val (_, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopOsmUid = OsmUID.from(OsmElementType.NODE, "12345")
        transaction {
            ShopTable.insert {
                it[osmUID] = shopOsmUid.asStr
                it[creationTime] = 100
                it[creatorUserId] = UUID.fromString(userId)
                it[lat] = 10.0
                it[lon] = 10.0
                it[lastAutoValidationTime] = null
            }
            val validationTimes = transaction {
                ShopTable.selectAll().map { it[ShopTable.lastAutoValidationTime] }
            }
            assertTrue(validationTimes.any { it == null })
        }

        withPlanteTestApplication {
            ShopsValidationWorker.waitUntilIdle()
            val validationTimes = transaction {
                ShopTable.selectAll().map { it[ShopTable.lastAutoValidationTime] }
            }
            assertFalse(validationTimes.any { it == null })
        }
    }

    @Test
    fun `on startup DOES NOT validate valid shops`() {
        val (userToken, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopOsmUid = OsmUID.from(OsmElementType.NODE, "12345")
        transaction {
            ShopTable.insert {
                it[osmUID] = shopOsmUid.asStr
                it[creationTime] = 100
                it[creatorUserId] = UUID.fromString(userId)
                it[lat] = BAD_COORD
                it[lon] = BAD_COORD
                it[lastAutoValidationTime] = 123
            }
        }

        withPlanteTestApplication {
            ShopsValidationWorker.waitUntilIdle()

            val shopsDataRequestBody = """ { "osm_uids": [ "${shopOsmUid.asStr}" ] } """
            val map = authedGet(userToken, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shop = shopFrom(map, shopOsmUid)

            assertEquals(shopOsmUid.asStr, shop["osm_uid"])
            assertNotEquals(VALIDATED_COORD, shop["lat"])
            assertNotEquals(VALIDATED_COORD, shop["lon"])
            assertEquals(BAD_COORD, shop["lat"])
            assertEquals(BAD_COORD, shop["lon"])
        }
    }

    @Test
    fun `limits number of simultaneous validations`() {
        val (userToken, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopOsmUids = mutableListOf<OsmUID>()
        transaction {
            for (index in 0 until ShopsValidationWorker.SINGLE_VALIDATION_SHOPS_COUNT_MAX * 3) {
                shopOsmUids += OsmUID.from(OsmElementType.NODE, "$index")
                ShopTable.insert {
                    it[osmUID] = shopOsmUids.last().asStr
                    it[creationTime] = 100
                    it[creatorUserId] = UUID.fromString(userId)
                    it[lat] = null
                    it[lon] = null
                    it[lastAutoValidationTime] = null
                }
            }
        }

        withPlanteTestApplication {
            ShopsValidationWorker.waitUntilIdle()
            assertEquals(3, requestedOsmUidsBatches.size)
            assertEquals(shopOsmUids.toSet(), allRequestedOsmUids.toSet())

            val shopsDataRequestBody = """ { "osm_uids": [ ${shopOsmUids.joinToString(",") { "\"$it\"" }} ] } """
            val map = authedGet(userToken, "/shops_data/", body = shopsDataRequestBody).jsonMap()
            val shops = shopsFrom(map)
            assertEquals(shopOsmUids.map { it.asStr }.toSet(), shops.map { it["osm_uid"] }.toSet())
        }
    }

    @Test
    fun `validations of shops with null coords have high priority`() {
        testHigherPriorityOf(ShopValidationReason.COORDS_WERE_NULL)
    }

    @Test
    fun `validations of shops without validation time have high priority`() {
        testHigherPriorityOf(ShopValidationReason.NEVER_VALIDATED_BEFORE)
    }

    private fun testHigherPriorityOf(reason: ShopValidationReason) {
        val (_, userId) = withPlanteTestApplication { registerAndGetTokenWithID() }
        val shopIds = mutableListOf<Int>()
        val shopUIDs = mutableListOf<OsmUID>()
        transaction {
            for (index in 0 until ShopsValidationWorker.SINGLE_VALIDATION_SHOPS_COUNT_MAX * 2) {
                shopUIDs += OsmUID.from(OsmElementType.NODE, "$index")
                val insertedShop = ShopTable.insert {
                    it[osmUID] = shopUIDs.last().asStr
                    it[creationTime] = 100
                    it[creatorUserId] = UUID.fromString(userId)
                    it[lat] = 123.0
                    it[lon] = 123.0
                    it[lastAutoValidationTime] = 123
                }
                shopIds += insertedShop[ShopTable.id]
            }
        }

        withPlanteTestApplication {
            val tasks = mutableListOf<ShopsValidationWorkerTask>()
            val max = ShopsValidationWorker.SINGLE_VALIDATION_SHOPS_COUNT_MAX
            // First schedule some other type of tasks
            for (index in 0 until max) {
                tasks += ShopsValidationWorkerTask(
                    shopIds[index],
                    UUID.fromString(userId),
                    ShopValidationReason.SHOP_MOVED,
                )
            }
            // Now schedule tasks for null coords
            for (index in max until max * 2) {
                tasks += ShopsValidationWorkerTask(
                    shopIds[index],
                    UUID.fromString(userId),
                    reason,
                )
            }
            ShopsValidationWorker.scheduleValidation(tasks)

            ShopsValidationWorker.waitUntilIdle()
            // Verify last UIDs were validated first (because they have COORDS_WERE_NULL as their reason)
            assertEquals(requestedOsmUidsBatches.first().toSet(), shopUIDs.takeLast(max).toSet())
            assertEquals(requestedOsmUidsBatches.last().toSet(), shopUIDs.take(max).toSet())
        }
    }

    @Test
    fun `when OSM doesn't give any info about a shop it's scheduled to be manually validated`() {
        ShopsValidationWorker.osmForTests = { uids ->
            requestedOsmUidsBatches.add(uids.map { it.osmUID })
            emptySet()
        }

        withPlanteTestApplication {
            val moderator = registerModerator()
            // No moderation tasks at start
            var tasks = moderatorTasksFrom(authedGet(moderator, "/all_moderator_tasks_data/"))
            assertEquals(0, tasks.size, tasks.toString())

            // Put a product
            val barcode = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            val map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to osmUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            ShopsValidationWorker.waitUntilIdle()

            // Shop is expected to be auto-validated
            assertEquals(1, requestedOsmUidsBatches.size)
            val shopRow = transaction {
                ShopTable.select(ShopTable.osmUID eq osmUid.asStr).single()
            }
            assertNotNull(shopRow[ShopTable.lastAutoValidationTime])
            // But its coords are still expected to be null
            assertNull(shopRow[ShopTable.lat])
            assertNull(shopRow[ShopTable.lon])

            // Moderation tasks is expected to be created
            tasks = moderatorTasksFrom(
                authedGet(moderator, "/all_moderator_tasks_data/"),
                withType = ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION)
            val task = tasks.single()
            assertEquals(osmUid.asStr, task["osm_uid"])
            assertEquals(ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION.taskName, task["task_type"])
        }
    }

    @Test
    fun `when OSM gives info about a shop it's NOT scheduled to be manually validated`() {
        withPlanteTestApplication {
            // Put a product
            val barcode = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            val map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to osmUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            ShopsValidationWorker.waitUntilIdle()

            // Moderation tasks is expected to be NOT created
            val moderator = registerModerator()
            val tasks = moderatorTasksFrom(
                authedGet(moderator, "/all_moderator_tasks_data/"),
                withType = ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION)
            assertTrue(tasks.isEmpty(), tasks.toString())
        }
    }

    @Test
    fun `manually validated shops are not being auto-revalidated`() {
        ShopsValidationWorker.osmForTests = { uids ->
            requestedOsmUidsBatches.add(uids.map { it.osmUID })
            emptySet()
        }

        withPlanteTestApplication {
            // Put a product
            val barcode = UUID.randomUUID().toString()
            val osmUid = generateFakeOsmUID()
            val clientToken = register()
            val map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to osmUid.asStr)).jsonMap()
            assertEquals("ok", map["result"])
            ShopsValidationWorker.waitUntilIdle()

            // Moderation tasks is expected to be created
            val moderator = registerModerator()
            val tasks = moderatorTasksFrom(
                authedGet(moderator, "/all_moderator_tasks_data/"),
                withType = ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION)
            val task = tasks.single()
            assertEquals(osmUid.asStr, task["osm_uid"])
            assertEquals(ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION.taskName, task["task_type"])

            // There was only 1 validation
            assertEquals(1, requestedOsmUidsBatches.size)
        }

        // Now let's restart the app so that the validation worker would also restart
        withPlanteTestApplication {
            // The shop is not expected to not be re-auto-validated
            ShopsValidationWorker.waitUntilIdle()
            assertEquals(1, requestedOsmUidsBatches.size)

            // Moderation task is still expected to be 1
            val moderator = registerModerator()
            val tasks = moderatorTasksFrom(
                authedGet(moderator, "/all_moderator_tasks_data/"),
                withType = ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION)
            assertEquals(1, tasks.size, tasks.toString())
        }
    }

    private fun shopFrom(map: Map<*, *>, shopOsmUid: OsmUID): Map<*, *> {
        val shops = map["results_v2"] as Map<*, *>
        assertEquals(1, shops.size, shops.toString())
        return shops[shopOsmUid.asStr] as Map<*, *>
    }

    private fun shopsFrom(map: Map<*, *>): List<Map<*, *>> {
        val shops = map["results_v2"] as Map<*, *>
        return shops.values.map { it as Map<*, *> }
    }

    private fun moderatorTasksFrom(call: TestApplicationCall, withType: ModeratorTaskType? = null): List<Map<*, *>> {
        val allTasks = call.jsonMap()["tasks"] as List<*>
        var result = allTasks.map { it as Map<*, *> }
        if (withType != null) {
            result = result.filter { it["task_type"] == withType.taskName }
        }
        return result
    }
}

private fun ShopsValidationWorker.waitUntilIdle() {
    val idle = AtomicBoolean()
    runWhenIdle {
        idle.set(true)
    }
    @Suppress("ControlFlowWithEmptyBody")
    while (!idle.get());
}
