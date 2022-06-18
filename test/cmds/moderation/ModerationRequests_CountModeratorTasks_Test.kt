package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_CountModeratorTasks_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }


    @Test
    fun `count_moderator_tasks cmd`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Product 1
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("de", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Verify
            map = authedGet(moderatorClientToken, "/count_moderator_tasks/").jsonMap()
            val expectedResult = mapOf(
                "total_count" to 7,
                "langs_counts" to mapOf(
                    "en" to 2,
                    "nl" to 1,
                    "ru" to 2,
                    "de" to 1,
                )
            )
            assertEquals(expectedResult, map, map.toString())
        }
    }

    @Test
    fun `count_moderator_tasks cmd by simple user`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/count_moderator_tasks/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `count_moderator_tasks cmd doesn't include resolved tasks`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Product 1
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Resolve the RU task
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            val ruTask = tasks.find { it["lang"] == "ru" }!!
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/", mapOf(
                "taskId" to ruTask["id"].toString(),
                "performedAction" to "testing",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify the resolved task is not included
            map = authedGet(moderatorClientToken, "/count_moderator_tasks/").jsonMap()
            val expectedResult = mapOf(
                "total_count" to 3,
                "langs_counts" to mapOf(
                    "en" to 2,
                    "nl" to 1,
                )
            )
            assertEquals(expectedResult, map, map.toString())
        }
    }

    @Test
    fun `count_moderator_tasks excluding certain types`() {
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
            var map = authedGet(moderatorClientToken, "/count_moderator_tasks/").jsonMap()
            assertEquals(ModeratorTaskType.values().size, map["total_count"], map.toString())

            // Get all excluding certain
            map = authedGet(moderatorClientToken, "/count_moderator_tasks/", queryParamsLists = mapOf(
                "excludeTypes" to listOf(
                    ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.typeName,
                    ModeratorTaskType.USER_PRODUCT_REPORT.typeName))
            ).jsonMap()
            assertEquals(ModeratorTaskType.values().size - 2, map["total_count"], map.toString())
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
            var map = authedGet(moderatorClientToken, "/count_moderator_tasks/").jsonMap()
            assertEquals(ModeratorTaskType.values().size, map["total_count"], map.toString())

            // Get all excluding certain
            map = authedGet(moderatorClientToken, "/count_moderator_tasks/", queryParamsLists = mapOf(
                "includeTypes" to listOf(
                    ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.typeName,
                    ModeratorTaskType.USER_PRODUCT_REPORT.typeName))
            ).jsonMap()
            assertEquals(2, map["total_count"], map.toString())
        }
    }
}