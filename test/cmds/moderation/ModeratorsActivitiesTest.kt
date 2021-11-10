package vegancheckteam.plante_server.cmds.moderation

import io.ktor.server.testing.TestApplicationEngine
import java.util.UUID
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

class ModeratorsActivitiesTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `moderators_activities general test`() {
        withPlanteTestApplication {
            val user = register()
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)

            // No activities at start
            var tasks = getModeratorsActivities(moderator)
            assertEquals(0, tasks.size, tasks.toString())

            var now = 123

            // Make a report
            createModeratorTask(user, now = ++now)
            // Unresolved task is not considered to be a moderator activity
            tasks = getModeratorsActivities(moderator)
            assertEquals(0, tasks.size, tasks.toString())

            // Resolve a task and check activities
            resolveSomeTask(moderator, performedAction = "unique text", now = ++now)
            tasks = getModeratorsActivities(moderator)
            assertEquals(1, tasks.size, tasks.toString())
            assertEquals(moderatorId.toString(), tasks[0]["resolver"])
            assertEquals(now, tasks[0]["resolution_time"])
            assertEquals("unique text", tasks[0]["resolver_action"])

            // Another report
            createModeratorTask(user, now = ++now)
            // Still 1 activity
            tasks = getModeratorsActivities(moderator)
            assertEquals(1, tasks.size, tasks.toString())

            // Resolve a second task and check activities
            resolveSomeTask(moderator, performedAction = "second unique text", now = ++now)
            tasks = getModeratorsActivities(moderator)
            assertEquals(2, tasks.size, tasks.toString())
            assertEquals(moderatorId.toString(), tasks[0]["resolver"])
            assertEquals(now, tasks[0]["resolution_time"])
            assertEquals("second unique text", tasks[0]["resolver_action"])
            assertEquals(moderatorId.toString(), tasks[1]["resolver"])
            assertEquals(now - 2, tasks[1]["resolution_time"])
            assertEquals("unique text", tasks[1]["resolver_action"])
        }
    }

    @Test
    fun `moderators_activities 'since' param`() {
        withPlanteTestApplication {
            val user = register()
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)

            var now = 123

            // Make 2 reports
            createModeratorTask(user, now = ++now)
            createModeratorTask(user, now = ++now)

            // Resolve both tasks
            val firstResolvingTime = ++now
            resolveSomeTask(moderator, performedAction = "unique text 1", now = firstResolvingTime)
            val secondResolvingTime = ++now
            resolveSomeTask(moderator, performedAction = "unique text 2", now = secondResolvingTime)

            // Since epoch
            var tasks = getModeratorsActivities(moderator, since = 0)
            assertEquals(2, tasks.size, tasks.toString())
            assertEquals("unique text 2", tasks[0]["resolver_action"])
            assertEquals("unique text 1", tasks[1]["resolver_action"])

            // Since second task
            tasks = getModeratorsActivities(moderator, since = secondResolvingTime)
            assertEquals(1, tasks.size, tasks.toString())
            assertEquals("unique text 2", tasks[0]["resolver_action"])

            // Since First task
            tasks = getModeratorsActivities(moderator, since = firstResolvingTime)
            assertEquals(2, tasks.size, tasks.toString())
            assertEquals("unique text 2", tasks[0]["resolver_action"])
            assertEquals("unique text 1", tasks[1]["resolver_action"])
        }
    }

    @Test
    fun `moderators_activities cannot be obtained by normal user`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/moderators_activities/?since=0").jsonMap()
            assertEquals("denied", map["error"])
        }
    }


    fun TestApplicationEngine.getModeratorsActivities(
        moderator: String,
        since: Int = 0): List<Map<*, *>> {
        val map = authedGet(moderator, "/moderators_activities/?since=$since").jsonMap()
        return (map["result"] as List<*>).map { it as Map<*, *> }
    }

    fun TestApplicationEngine.createModeratorTask(
        user: String,
        now: Int) {
        val barcode = UUID.randomUUID().toString()
        val map = authedGet(user, "/make_report/", mapOf(
            "barcode" to barcode,
            "text" to "some text",
            "testingNow" to now.toString(),
        )).jsonMap()
        assertEquals("ok", map["result"])
    }

    fun TestApplicationEngine.resolveSomeTask(
        moderator: String,
        performedAction: String,
        now: Int) {
        var map = authedGet(moderator, "/all_moderator_tasks_data/", mapOf(
            "testingNow" to now.toString(),
        )).jsonMap()
        val allTasks = map["tasks"] as List<*>
        val task = allTasks[0] as Map<*, *>
        val taskId = task["id"]
        map = authedGet(moderator, "/resolve_moderator_task/", mapOf(
            "taskId" to "$taskId",
            "performedAction" to performedAction,
            "testingNow" to now.toString(),
        )).jsonMap()
        assertEquals("ok", map["result"])
    }
}
