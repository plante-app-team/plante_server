package vegancheckteam.plante_server.cmds.moderation

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import java.util.*
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.insert
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_General_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `moderator_task_data cmd`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/", mapOf(
                "barcode" to "123",
                "text" to "someText",
            )).jsonMap()
            assertEquals("ok", map["result"])

            val taskId = transaction {
                val row = ModeratorTaskTable.selectAll().first()
                row[ModeratorTaskTable.id]
            }

            // Check 1
            map = authedGet(moderatorClientToken, "/moderator_task_data/", mapOf(
                "taskId" to taskId.toString()
            )).jsonMap()
            assertEquals(taskId, map["id"])

            // Resolve
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/", mapOf(
                "taskId" to taskId.toString()
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Check 2
            map = authedGet(moderatorClientToken, "/moderator_task_data/", mapOf(
                "taskId" to taskId.toString()
            )).jsonMap()
            assertEquals("task_not_found", map["error"])
        }
    }

    @Test
    fun `all_moderator_tasks_data cmd with lang-related params`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var now = 1

            // Product 1
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown",
                "testingNow" to "${++now}"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "veganStatus" to "unknown",
                "testingNow" to "${++now}"),
                mapOf("langs" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown",
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown",
                "testingNow" to "${++now}"),
                mapOf("langs" to listOf("de", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Verify
            // No lang
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "onlyWithNoLang" to "true"
            )).jsonMap()
            var tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            assertEquals(barcode1, tasks[0]["barcode"])
            assertEquals(null, tasks[0]["lang"])

            // En
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "en"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(2, tasks.size, map.toString())
            var barcodes = tasks.map { it["barcode"] }
            var langs = tasks.map { it["lang"] }
            assertEquals(barcodes.toSet(), setOf(barcode1, barcode2))
            assertEquals(langs, listOf("en", "en"))

            // Ru
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "ru"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(2, tasks.size, map.toString())
            barcodes = tasks.map { it["barcode"] }
            langs = tasks.map { it["lang"] }
            assertEquals(barcodes.toSet(), setOf(barcode1, barcode2))
            assertEquals(langs, listOf("ru", "ru"))

            // Nl
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "nl"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            barcodes = tasks.map { it["barcode"] }
            langs = tasks.map { it["lang"] }
            assertEquals(barcodes.toSet(), setOf(barcode1))
            assertEquals(langs, listOf("nl"))

            // De
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "de"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            barcodes = tasks.map { it["barcode"] }
            langs = tasks.map { it["lang"] }
            assertEquals(barcodes.toSet(), setOf(barcode1))
            assertEquals(langs, listOf("de"))
        }
    }

    @Test
    fun `all_moderator_tasks_data cmd - provide both 'lang' and 'onlyWithNoLang'`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Verify
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "onlyWithNoLang" to "true",
                "lang" to "en",
            )).jsonMap()
            assertEquals("invalid_params", map["error"])
        }
    }

    @Test
    fun `if product veg status was set by a moderator, normal user cannot change it`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create product by user
            var map = authedGet(clientToken, "/create_update_product/", mapOf(
                "barcode" to barcode,
                "veganStatus" to "unknown",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Change its status
            map = authedGet(clientToken, "/create_update_product/", mapOf(
                "barcode" to barcode,
                "veganStatus" to "positive",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify status changed
            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("positive", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            // Verify moderator task exists
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            assertEquals(barcode, tasks[0]["barcode"])

            // Moderate the product
            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/", mapOf(
                "barcode" to barcode,
                "veganStatus" to "negative",
            )).jsonMap()
            assertEquals("ok", map["result"])
            // Resolve moderator task
            val taskId = transaction {
                val row = ModeratorTaskTable.selectAll().first()
                row[ModeratorTaskTable.id]
            }
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // Try to change product's status by the user again
            map = authedGet(clientToken, "/create_update_product/", mapOf(
                "barcode" to barcode,
                "veganStatus" to "positive",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify status didn't change
            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("negative", map["vegan_status"])
            assertEquals("moderator", map["vegan_status_source"])

            // Verify a moderator task was not created
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(0, tasks.size, map.toString())
        }
    }

    @Test
    fun `all tasks excluding certain types`() {
        withPlanteTestApplication {
            val (_, userId) = registerAndGetTokenWithID()
            transaction {
                for (type in ModeratorTaskType.values()) {
                    ModeratorTaskTable.insert {
                        it[productBarcode] = "123"
                        it[taskType] = type.persistentCode
                        it[taskSourceUserId] = UUID.fromString(userId)
                        it[creationTime] = now()
                    }
                }
            }

            val moderatorClientToken = registerModerator()

            // Get all
            var map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(ModeratorTaskType.values().size, tasks.size, map.toString())
            var types = tasks.map { it["task_type"] as String }
            assertEquals(ModeratorTaskType.values().map { it.typeName }.toSet(), types.toSet())

            // Get all excluding certain
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", queryParamsLists = mapOf(
                "excludeTypes" to listOf(
                        ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.typeName,
                        ModeratorTaskType.USER_REPORT.typeName))
            ).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            val expectedTypes = ModeratorTaskType
                .values()
                .filter { it != ModeratorTaskType.PRODUCT_CHANGE_IN_OFF && it != ModeratorTaskType.USER_REPORT }
            assertEquals(expectedTypes.size, tasks.size, map.toString())
            types = tasks.map { it["task_type"] as String }
            assertEquals(expectedTypes.map { it.typeName }.toSet(), types.toSet())
        }
    }

    @Test
    fun `all tasks but with specified types`() {
        withPlanteTestApplication {
            val (_, userId) = registerAndGetTokenWithID()
            transaction {
                for (type in ModeratorTaskType.values()) {
                    ModeratorTaskTable.insert {
                        it[productBarcode] = "123"
                        it[taskType] = type.persistentCode
                        it[taskSourceUserId] = UUID.fromString(userId)
                        it[creationTime] = now()
                    }
                }
            }

            val moderatorClientToken = registerModerator()

            // Get all
            var map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(ModeratorTaskType.values().size, tasks.size, map.toString())
            var types = tasks.map { it["task_type"] as String }
            assertEquals(ModeratorTaskType.values().map { it.typeName }.toSet(), types.toSet())

            // Get all excluding certain
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", queryParamsLists = mapOf(
                "includeTypes" to listOf(
                    ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.typeName,
                    ModeratorTaskType.USER_REPORT.typeName))
            ).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            val expectedTypes = listOf(ModeratorTaskType.PRODUCT_CHANGE_IN_OFF, ModeratorTaskType.USER_REPORT)
            assertEquals(expectedTypes.size, tasks.size, map.toString())
            types = tasks.map { it["task_type"] as String }
            assertEquals(expectedTypes.map { it.typeName }.toSet(), types.toSet())
        }
    }
}
