package vegancheckteam.plante_server.cmds.moderation

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import java.util.*
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.insert
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_Pagination_Test {
    private val tasksTypes = ModeratorTaskType.values().sortedBy { it.priority }.take(4)

    @Before
    fun setUp() {
        if (tasksTypes.size != 4) {
            throw Error("Tests rely on the fact there are at least 4 distinct priorities")
        }

        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            // Let's create a complex pagination scenario.
            // Each priority will have 0 or several tasks.
            // Pagination requests in tests will always have page size set to 5,
            // but offset of each request will be different.
            // Tasks priorities and how many tasks of these priorities will be there:
            //
            // priority0: task, task, task (count: 3, offset: 0)
            // priority1: task, task, task, task, task, task (count: 6, offset: 3)
            // priority2: - (count: 0, offset: -)
            // priority3: task, task, task, task, task, task, task, task, task, task, task, task (count: 12, offset: 10)
            //
            // ...where "count" is how many tasks of the priority are there,
            // "offset" if the offset from the first task of priority0.
            val user = registerAndGetTokenWithID().second
            var now = 0L
            transaction {
                for (i in (1..3)) {
                    ModeratorTaskTable.insert {
                        it[productBarcode] = "123"
                        it[taskType] = tasksTypes[0].persistentCode
                        it[taskSourceUserId] = UUID.fromString(user)
                        it[creationTime] = ++now
                    }
                }
                for (i in (1..6)) {
                    ModeratorTaskTable.insert {
                        it[productBarcode] = "123"
                        it[taskType] = tasksTypes[1].persistentCode
                        it[taskSourceUserId] = UUID.fromString(user)
                        it[creationTime] = ++now
                    }
                }
                // No tasks of priority2
//                for (i in (1..N)) {
//                    ModeratorTaskTable.insert {
//                        it[taskType] = tasksTypes[2].persistentCode
//                    }
//                }
                for (i in (1..12)) {
                    ModeratorTaskTable.insert {
                        it[productBarcode] = "123"
                        it[taskType] = tasksTypes[3].persistentCode
                        it[taskSourceUserId] = UUID.fromString(user)
                        it[creationTime] = ++now
                    }
                }
            }
        }
    }

    @Test
    fun `complex pagination scenario page 0`() {
        withPlanteTestApplication {
            val moderator = registerModerator()
            val map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
                "page" to "0",
                "pageSize" to "5",
            )).jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }

            assertEquals(5, tasks.size, tasks.toString())
            // Verify task types
            assertEquals(tasksTypes[0].taskName, tasks[0]["task_type"], tasks.toString())
            assertEquals(tasksTypes[0].taskName, tasks[1]["task_type"], tasks.toString())
            assertEquals(tasksTypes[0].taskName, tasks[2]["task_type"], tasks.toString())
            assertEquals(tasksTypes[1].taskName, tasks[3]["task_type"], tasks.toString())
            assertEquals(tasksTypes[1].taskName, tasks[4]["task_type"], tasks.toString())
            // Verify time-related order
            assertEquals(1, tasks[0]["creation_time"], tasks.toString())
            assertEquals(2, tasks[1]["creation_time"], tasks.toString())
            assertEquals(3, tasks[2]["creation_time"], tasks.toString())
            assertEquals(4, tasks[3]["creation_time"], tasks.toString())
            assertEquals(5, tasks[4]["creation_time"], tasks.toString())
        }
    }

    @Test
    fun `complex pagination scenario page 1`() {
        withPlanteTestApplication {
            val moderator = registerModerator()
            val map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
                "page" to "1",
                "pageSize" to "5",
            )).jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }

            assertEquals(5, tasks.size, tasks.toString())
            // Verify task types
            assertEquals(tasksTypes[1].taskName, tasks[0]["task_type"], tasks.toString())
            assertEquals(tasksTypes[1].taskName, tasks[1]["task_type"], tasks.toString())
            assertEquals(tasksTypes[1].taskName, tasks[2]["task_type"], tasks.toString())
            assertEquals(tasksTypes[1].taskName, tasks[3]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[4]["task_type"], tasks.toString())
            // Verify time-related order
            assertEquals(6, tasks[0]["creation_time"], tasks.toString())
            assertEquals(7, tasks[1]["creation_time"], tasks.toString())
            assertEquals(8, tasks[2]["creation_time"], tasks.toString())
            assertEquals(9, tasks[3]["creation_time"], tasks.toString())
            assertEquals(10, tasks[4]["creation_time"], tasks.toString())
        }
    }

    @Test
    fun `complex pagination scenario page 2`() {
        withPlanteTestApplication {
            val moderator = registerModerator()
            val map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
                "page" to "2",
                "pageSize" to "5",
            )).jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }

            assertEquals(5, tasks.size, tasks.toString())
            // Verify task types
            assertEquals(tasksTypes[3].taskName, tasks[0]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[1]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[2]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[3]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[4]["task_type"], tasks.toString())
            // Verify time-related order
            assertEquals(11, tasks[0]["creation_time"], tasks.toString())
            assertEquals(12, tasks[1]["creation_time"], tasks.toString())
            assertEquals(13, tasks[2]["creation_time"], tasks.toString())
            assertEquals(14, tasks[3]["creation_time"], tasks.toString())
            assertEquals(15, tasks[4]["creation_time"], tasks.toString())
        }
    }

    @Test
    fun `complex pagination scenario page 3`() {
        withPlanteTestApplication {
            val moderator = registerModerator()
            val map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
                "page" to "3",
                "pageSize" to "5",
            )).jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }

            assertEquals(5, tasks.size, tasks.toString())
            // Verify task types
            assertEquals(tasksTypes[3].taskName, tasks[0]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[1]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[2]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[3]["task_type"], tasks.toString())
            assertEquals(tasksTypes[3].taskName, tasks[4]["task_type"], tasks.toString())
            // Verify time-related order
            assertEquals(16, tasks[0]["creation_time"], tasks.toString())
            assertEquals(17, tasks[1]["creation_time"], tasks.toString())
            assertEquals(18, tasks[2]["creation_time"], tasks.toString())
            assertEquals(19, tasks[3]["creation_time"], tasks.toString())
            assertEquals(20, tasks[4]["creation_time"], tasks.toString())
        }
    }

    @Test
    fun `complex pagination scenario page 4`() {
        withPlanteTestApplication {
            val moderator = registerModerator()
            val map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
                "page" to "4",
                "pageSize" to "5",
            )).jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }

            assertEquals(1, tasks.size, tasks.toString())
            // Verify task types
            assertEquals(tasksTypes[3].taskName, tasks[0]["task_type"], tasks.toString())
            // Verify time-related order
            assertEquals(21, tasks[0]["creation_time"], tasks.toString())
        }
    }
}