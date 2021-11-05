package vegancheckteam.plante_server.cmds.moderation

import java.time.ZonedDateTime
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_TasksResolving_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `can resolve task`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign the task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            // 1 task expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())
            val taskId = (tasks[0] as Map<*, *>)["id"]

            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // 0 tasks expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(0, allTasks.size, map.toString())
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())
        }
    }

    @Test
    fun `can get resolved tasks if want to`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign the task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            val taskId = (allTasks[0] as Map<*, *>)["id"]

            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // Now let's get all tasks including resolved
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/?includeResolved=true").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            // Now let's get assigned tasks including resolved
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/?includeResolved=true").jsonMap()
            var tasks = map["tasks"] as List<*>
            assertEquals(1, tasks.size, map.toString())

            // Now let's repeat that without 'includeResolved' just in case
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(0, allTasks.size, map.toString())
            map = authedGet(moderatorClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            tasks = map["tasks"] as List<*>
            assertEquals(0, tasks.size, map.toString())
        }
    }

    @Test
    fun `cannot resolve not existing task`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=100").jsonMap()
            assertEquals("task_not_found", map["error"])
        }
    }

    @Test
    fun `resolved tasks are deleted after some time`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign the task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])
            // Get task ID
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val taskId = (allTasks[0] as Map<*, *>)["id"]
            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // 1 task still expected to exist
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/?includeResolved=true").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())

            // Pass some time
            val now = ZonedDateTime.now().toEpochSecond() + DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS * 24 * 60 * 60 + 1

            // 0 tasks expected to exist now
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/?includeResolved=true&testingNow=$now").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(0, allTasks.size, map.toString())
        }
    }

    @Test
    fun `resolved tasks cannot be randomly assigned`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Assign the task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("ok", map["result"])
            // Get task ID
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val taskId = (allTasks[0] as Map<*, *>)["id"]
            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // Reassign the task
            map = authedGet(moderatorClientToken, "/assign_moderator_task/").jsonMap()
            assertEquals("no_unresolved_moderator_tasks", map["error"])
        }
    }

    @Test
    fun `cannot resolve task by simple user`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Get task ID
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val taskId = (allTasks[0] as Map<*, *>)["id"]

            // Resolve the task by simple user
            map = authedGet(simpleUserClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `can unresolve task`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Get task ID
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val taskId = (allTasks[0] as Map<*, *>)["id"]

            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // 0 tasks now
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(0, allTasks.size)

            // Unresolve the task
            map = authedGet(moderatorClientToken, "/unresolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // 1 task now
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size)
        }
    }

    @Test
    fun `cannot unresolve task by simple user`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val simpleUserClientToken = register()

            // Make a report
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=222&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Get task ID
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            var allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())
            val taskId = (allTasks[0] as Map<*, *>)["id"]

            // Resolve the task
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // 0 tasks now
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            allTasks = map["tasks"] as List<*>
            assertEquals(0, allTasks.size)

            // Unresolve the task by simple user
            map = authedGet(simpleUserClientToken, "/unresolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
