package vegancheckteam.plante_server

import io.ktor.server.testing.withTestApplication
import org.jetbrains.exposed.sql.deleteAll
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

class ChangeModeratorTaskLangTest {
    @Before
    fun setUp() {
        withTestApplication({ module(testing = true) }) {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `can change task lang`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(simpleUserClientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "langs" to "en")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify lang
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            var task = allTasks[0] as Map<*, *>
            assertEquals("en", task["lang"])
            val taskId = task["id"]

            // Change lang
            map = authedGet(moderatorClientToken, "/change_moderator_task_lang/", mapOf(
                "taskId" to taskId.toString(),
                "lang" to "ru",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify lang
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            task = allTasks[0] as Map<*, *>
            assertEquals("ru", task["lang"])

            // Erase lang
            map = authedGet(moderatorClientToken, "/change_moderator_task_lang/", mapOf(
                "taskId" to taskId.toString(),
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify lack of lang
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            task = allTasks[0] as Map<*, *>
            assertEquals(null, task["lang"])
        }
    }

    @Test
    fun `cannot change task lang by simple user`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(simpleUserClientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "langs" to "en")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify lang
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val task = allTasks[0] as Map<*, *>
            assertEquals("en", task["lang"])
            val taskId = task["id"]

            // Change lang
            map = authedGet(simpleUserClientToken, "/change_moderator_task_lang/", mapOf(
                "taskId" to taskId.toString(),
                "lang" to "ru",
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
