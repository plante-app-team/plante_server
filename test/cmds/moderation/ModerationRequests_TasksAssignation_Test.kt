package vegancheckteam.plante_server.cmds.moderation

import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_TasksAssignation_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `assign random task to requester`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

            // No tasks at first
            var map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals(emptyList<Any>(), map["tasks"])

            // Make a report
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Still no tasks
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals(emptyList<Any>(), map["tasks"])

            // Assign one to us
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // Now we've got a task!
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            val tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            val task = tasks[0] as Map<*, *>
            assertEquals("123", task["barcode"])
            assertEquals("user_report", task["task_type"])
            assertEquals("someText", task["text_from_user"])
            assertEquals(moderatorId.toString(), task["assignee"])
            assertEquals(simpleUserId, task["task_source_user_id"])
            val now = ZonedDateTime.now().toEpochSecond()
            val creationTime = task["creation_time"] as Int
            val assignTime = task["assign_time"] as Int
            assertTrue(abs(now - creationTime) < 2) // no more than 2 secs
            assertTrue(abs(now - assignTime) < 2) // no more than 2 secs
        }
    }

    @Test
    fun `assign certain task to requester`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, _) = registerAndGetTokenWithID()

            // Make 3 reports separated by 1 second
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=111&text=someText").jsonMap()
            assertEquals("ok", map["result"])
            Thread.sleep(1000)
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])
            Thread.sleep(1000)
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=333&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // 3 unassigned tasks expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(3, allTasks.size, map.toString())
            val unassignedTask1 = allTasks[0] as Map<*, *>
            assertEquals("111", unassignedTask1["barcode"])
            assertEquals(null, unassignedTask1["assignee"])
            assertEquals(null, unassignedTask1["assign_time"])
            val unassignedTask2 = allTasks[1] as Map<*, *>
            assertEquals("222", unassignedTask2["barcode"])
            assertEquals(null, unassignedTask2["assignee"])
            assertEquals(null, unassignedTask2["assign_time"])
            val unassignedTask3 = allTasks[2] as Map<*, *>
            assertEquals("333", unassignedTask3["barcode"])
            assertEquals(null, unassignedTask3["assignee"])
            assertEquals(null, unassignedTask3["assign_time"])

            // Assign the middle task to us
            map = authedGet(moderatorClientToken, "/assign_moderator_task/?taskId=${unassignedTask2["id"]}").jsonMap()
            assertEquals("ok", map["result"])

            // Now we've got a task!
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            val tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            val task = tasks[0] as Map<*, *>
            assertEquals("222", task["barcode"])
            assertEquals(moderatorId.toString(), task["assignee"])
        }
    }

    @Test
    fun `assign random task to certain moderator`() {
        withPlanteTestApplication {
            val moderatorId1 = UUID.randomUUID()
            val moderatorClientToken1 = registerModerator(moderatorId1)
            val moderatorId2 = UUID.randomUUID()
            val moderatorClientToken2 = registerModerator(moderatorId2)

            val (simpleUserClientToken, _) = registerAndGetTokenWithID()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign task to second moderator by first
            map = authedGet(moderatorClientToken1, "/assign_moderator_task/?assignee=${moderatorId2}").jsonMap()
            assertEquals("ok", map["result"])

            // First moderator has no tasks
            map = authedGet(moderatorClientToken1, "/assigned_moderator_tasks_data/").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())

            // Second moderator has 1 task
            map = authedGet(moderatorClientToken2, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
        }
    }

    @Test
    fun `tasks assigned too long ago are not considered assigned`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, _) = registerAndGetTokenWithID()

            // Make a report and assign
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // Assigned tasks give the task
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            // All tasks have an assigned value
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            var task = tasks[0] as Map<*, *>
            assertEquals(moderatorId.toString(), task["assignee"])
            assertNotNull(task["assign_time"])

            // A few minutes later
            val now = ZonedDateTime.now().toEpochSecond() + ASSIGNATION_TIME_LIMIT_MINUTES * 60 + 1

            // Request assigned tasks again but assume some time to have passed
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/?testingNow=$now").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())

            // Request all tasks again but assume some time to have passed
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/?testingNow=$now").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            task = tasks[0] as Map<*, *>
            assertEquals(null, task["assignee"])
            assertEquals(null, task["assign_time"])
        }
    }

    @Test
    fun `no_unresolved_moderator_tasks error when there are no tasks to assign`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)

            // No reports yet
            var map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])

            // Make a report
            val (simpleUserClientToken, _) = registerAndGetTokenWithID()
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign the report
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // Try to assign another task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
        }
    }

    @Test
    fun `cannot assign task by a simple user`() {
        withPlanteTestApplication {
            val (simpleUserClientToken, _) = registerAndGetTokenWithID()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign task by a simple user
            map = authedGet(simpleUserClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `cannot assign task to a simple user`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign task to a simple user
            map = authedGet(moderatorClientToken, "/assign_moderator_task/?assignee=$simpleUserId").jsonMap()
            assertEquals("assignee_not_moderator", map["error"])
        }
    }

    @Test
    fun `cannot give list of assigned tasks to a simple user`() {
        withPlanteTestApplication {
            val simpleUserClientToken = register()
            val map = authedGet(simpleUserClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `cannot give list of all tasks to a simple user`() {
        withPlanteTestApplication {
            val simpleUserClientToken = register()
            val map = authedGet(simpleUserClientToken, "/all_moderator_tasks_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `reports tasks have higher priority when assigning a random task`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, _) = registerAndGetTokenWithID()

            // Make 3 tasks separated by 1 second
            // Create a product
            var map = authedGet(simpleUserClientToken, "/create_update_product/?barcode=111").jsonMap()
            assertEquals("ok", map["result"])
            Thread.sleep(1000)
            // Make a report
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])
            Thread.sleep(1000)
            // Create another product
            map = authedGet(simpleUserClientToken, "/create_update_product/?barcode=333").jsonMap()
            assertEquals("ok", map["result"])

            // 3 tasks expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(3, allTasks.size, map.toString())

            // Assign a random task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // Ensure we've got the report task
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            val tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            val task = tasks[0] as Map<*, *>
            assertEquals("222", task["barcode"])
            assertEquals(moderatorId.toString(), task["assignee"])
            assertEquals(moderatorId.toString(), task["assignee"])
            assertEquals("user_report", task["task_type"])
        }
    }

    @Test
    fun `moderator can reject a task`() {
        withPlanteTestApplication {
            val moderatorId1 = UUID.randomUUID()
            val moderatorId2 = UUID.randomUUID()
            val moderatorClientToken1 = registerModerator(id = moderatorId1)
            val moderatorClientToken2 = registerModerator(id = moderatorId2)

            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/", mapOf(
                "barcode" to "123",
                "text" to "someText",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Moderator 1

            // Moderator 1 Assign
            map = authedGet(moderatorClientToken1, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])
            // Moderator 1 Check
            map = authedGet(moderatorClientToken1, "/assigned_moderator_tasks_data/").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            // Moderator 1 Reject
            val task = tasks[0] as Map<*, *>
            val taskId = task["id"].toString()
            map = authedGet(moderatorClientToken1, "/reject_moderator_task/", mapOf(
                "taskId" to taskId,
            )).jsonMap()
            assertEquals("ok", map["result"])
            // Moderator 1 Check
            map = authedGet(moderatorClientToken1, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())
            // Moderator 1 Assign attempt 2
            map = authedGet(moderatorClientToken1, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])

            // Moderator 2

            // Moderator 2 Assign
            map = authedGet(moderatorClientToken2, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])
            // Moderator 2 Check
            map = authedGet(moderatorClientToken2, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            // Moderator 2 Reject
            map = authedGet(moderatorClientToken2, "/reject_moderator_task/", mapOf(
                "taskId" to taskId,
            )).jsonMap()
            assertEquals("ok", map["result"])
            // Moderator 2 Check
            map = authedGet(moderatorClientToken2, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())
            // Moderator 2 Assign attempt 2
            map = authedGet(moderatorClientToken2, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
        }
    }

    @Test
    fun `rejected task can be manually assigned`() {
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

            // Assign
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // Check
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            // Reject
            val task = tasks[0] as Map<*, *>
            val taskId = task["id"].toString()
            map = authedGet(moderatorClientToken, "/reject_moderator_task/", mapOf(
                "taskId" to taskId,
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Check
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())

            // Assign attempt 2
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])

            // Manual assign!
            map = authedGet(moderatorClientToken, "/assign_moderator_task/", mapOf(
                "taskId" to taskId
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Check
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
        }
    }

    @Test
    fun `random task assignment when moderator has known langs specified`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val clientToken = register()

            // Set langs the moderator knows
            var map = authedGet(moderatorClientToken,
                "/update_user_data/",
                queryParamsLists = mapOf("langsPrioritized" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            // Create moderator tasks
            // En
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown",
                "langs" to "en")).jsonMap()
            assertEquals("ok", map["result"])

            // Ru
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "veganStatus" to "unknown",
                "langs" to "ru")).jsonMap()
            assertEquals("ok", map["result"])

            // De
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "veganStatus" to "unknown",
                "langs" to "de")).jsonMap()
            assertEquals("ok", map["result"])

            // No lang
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "veganStatus" to "unknown")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify
            val tasks = mutableListOf<Map<*, *>>()

            // We expect 3 tasks
            for (index in 0 until 3) {
                map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
                assertEquals("ok", map["result"])
                map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
                tasks.addAll((map["tasks"] as List<*>).map { it as Map<*, *> })
                map = authedGet(moderatorClientToken, "/resolve_moderator_task/", mapOf(
                    "taskId" to tasks.last()["id"].toString(),
                    "performedAction" to "testing",
                )).jsonMap()
                assertEquals("ok", map["result"])
            }
            // The expected tasks are of: en, ru, and of no language
            val langs = tasks.map { it["lang"] }
            assertEquals(3, langs.size)
            assertTrue(langs.contains("en"))
            assertTrue(langs.contains("ru"))
            assertTrue(langs.contains(null))

            // The 'de' tasks is not expected to be assigned because the
            // moderator doesn't know the lang
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
        }
    }
}
