package vegancheckteam.plante_server

import cmds.moderation.ASSIGNATION_TIME_LIMIT_MINUTES
import cmds.moderation.DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS
import io.ktor.server.testing.withTestApplication
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.cmds.MAX_REPORTS_FOR_PRODUCT_TESTING
import vegancheckteam.plante_server.cmds.MAX_REPORTS_FOR_USER_TESTING
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MAX_LENGTH
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MIN_LENGTH
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vegancheckteam.plante_server.test_utils.registerModeratorOfEverything

class ModerationRequestsTest {
    @Before
    fun setUp() {
        withTestApplication({ module(testing = true) }) {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `product creation and change create moderator tasks`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()

            // No moderator task yet
            val tasksCount = transaction {
                ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode
                }.count()
            }
            assertEquals(0, tasksCount)

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            // Now there is a task
            transaction {
                val tasks = ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode
                }.toList()
                assertEquals(1, tasks.count())
                assertEquals(ModeratorTaskType.PRODUCT_CHANGE.persistentCode, tasks[0][ModeratorTaskTable.taskType])
            }

            // Clear
            transaction {
                ModeratorTaskTable.deleteWhere {
                    ModeratorTaskTable.productBarcode eq barcode
                }
            }

            // Update the product
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            // Now there is a task again
            transaction {
                val tasks = ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode
                }.toList()
                assertEquals(1, tasks.count())
                assertEquals(ModeratorTaskType.PRODUCT_CHANGE.persistentCode, tasks[0][ModeratorTaskTable.taskType])
            }
        }
    }

    @Test
    fun `there's always no more than 1 moderator task of PRODUCT_CHANGE type`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            // Update the product
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 1 task only
            transaction {
                val tasks = ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode
                }.toList()
                assertEquals(1, tasks.count())
                assertEquals(ModeratorTaskType.PRODUCT_CHANGE.persistentCode, tasks[0][ModeratorTaskTable.taskType])
            }
        }
    }

    @Test
    fun `several different products can have their own PRODUCT_CHANGE moderator tasks`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Create product1
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            // Create product2
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&vegetarianStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 2 tasks, 1 for each product
            transaction {
                val tasks1 = ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode1
                }
                assertEquals(1, tasks1.count())
                val tasks2 = ModeratorTaskTable.select {
                    ModeratorTaskTable.productBarcode eq barcode2
                }
                assertEquals(1, tasks2.count())
            }
        }
    }

    @Test
    fun `make_reports command`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/make_report/?barcode=${barcode}&text=text1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/make_report/?barcode=${barcode}&text=text2").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/make_report/?barcode=${barcode}&text=text3").jsonMap()
            assertEquals("ok", map["result"])

            transaction {
                val tasks = ModeratorTaskTable.select {
                    (ModeratorTaskTable.productBarcode eq barcode) and
                            (ModeratorTaskTable.taskType eq ModeratorTaskType.USER_REPORT.persistentCode)
                }.toList()
                assertEquals(3, tasks.count())

                val texts = tasks.map { it[ModeratorTaskTable.textFromUser] }
                assertTrue("text1" in texts);
                assertTrue("text2" in texts);
                assertTrue("text3" in texts);
            }
        }
    }

    @Test
    fun `make_reports max reports for user`() {
        withTestApplication({ module(testing = true) }) {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            // NOTE: 'MAX_REPORTS_FOR_PRODUCT - 2' is because
            // product creation also creates a moderator task
            // and there are 2 products
            for (index in 0 until MAX_REPORTS_FOR_USER_TESTING - 2) {
                val barcode = if (index % 2 == 1) {
                    barcode1
                } else {
                    barcode2
                }
                map = authedGet(
                    clientToken, "/make_report/?barcode=${barcode}&text=text$index").jsonMap()
                assertEquals("ok", map["result"], map.toString())
            }

            // Ensure there's a max number of tasks
            transaction {
                assertEquals(MAX_REPORTS_FOR_USER_TESTING, ModeratorTaskTable.selectAll().count().toInt())
            }

            // Error expected now
            map = authedGet(
                clientToken, "/make_report/?barcode=${barcode1}&text=finaltext1").jsonMap()
            assertEquals("too_many_reports_for_user", map["error"])
            map = authedGet(
                clientToken, "/make_report/?barcode=${barcode2}&text=finaltext2").jsonMap()
            assertEquals("too_many_reports_for_user", map["error"])

            // Ensure there's still a max number of tasks
            transaction {
                assertEquals(MAX_REPORTS_FOR_USER_TESTING, ModeratorTaskTable.selectAll().count().toInt())
            }
        }
    }

    @Test
    fun `make_reports max reports for product`() {
        withTestApplication({ module(testing = true) }) {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken1 = register()
            val clientToken2 = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken1, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            // NOTE: 'MAX_REPORTS_FOR_PRODUCT - 1' is because
            // product creation also creates a moderator task
            for (index in 0 until MAX_REPORTS_FOR_PRODUCT_TESTING - 1) {
                val clientToken = if (index % 2 == 1) {
                    clientToken1
                } else {
                    clientToken2
                }
                map = authedGet(
                    clientToken, "/make_report/?barcode=${barcode}&text=text$index").jsonMap()
                assertEquals("ok", map["result"], map.toString())
            }

            // Ensure there's a max number of tasks
            transaction {
                assertEquals(MAX_REPORTS_FOR_PRODUCT_TESTING, ModeratorTaskTable.selectAll().count().toInt())
            }

            // Error expected now
            map = authedGet(
                clientToken1, "/make_report/?barcode=${barcode}&text=finaltext1").jsonMap()
            assertEquals("too_many_reports_for_product", map["error"])
            map = authedGet(
                clientToken2, "/make_report/?barcode=${barcode}&text=finaltext1").jsonMap()
            assertEquals("too_many_reports_for_product", map["error"])

            // Ensure there's still a max number of tasks
            transaction {
                assertEquals(MAX_REPORTS_FOR_PRODUCT_TESTING, ModeratorTaskTable.selectAll().count().toInt())
            }
        }
    }

    @Test
    fun `make_reports command min and max text lengths`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            val text1 = "a".repeat(REPORT_TEXT_MIN_LENGTH - 1)
            map = authedGet(clientToken, "/make_report/?barcode=${barcode}&text=$text1").jsonMap()
            assertEquals("report_text_too_short", map["error"])

            val text2 = "a".repeat(REPORT_TEXT_MAX_LENGTH + 1)
            map = authedGet(clientToken, "/make_report/?barcode=${barcode}&text=$text2").jsonMap()
            assertEquals("report_text_too_long", map["error"])

            // No reports should be created
            transaction {
                val tasks = ModeratorTaskTable.select {
                    (ModeratorTaskTable.productBarcode eq barcode) and
                            (ModeratorTaskTable.taskType eq ModeratorTaskType.USER_REPORT.persistentCode)
                }
                assertEquals(0, tasks.count())
            }
        }
    }

    @Test
    fun `assign random task to requester`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val simpleUserClientToken = register()
            val map = authedGet(simpleUserClientToken, "/assigned_moderator_tasks_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `cannot give list of all tasks to a simple user`() {
        withTestApplication({ module(testing = true) }) {
            val simpleUserClientToken = register()
            val map = authedGet(simpleUserClientToken, "/all_moderator_tasks_data/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `reports tasks have higher priority when assigning a random task`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

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
    fun `can resolve task`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
            val moderatorClientToken = registerModerator()
            val map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=100").jsonMap()
            assertEquals("task_not_found", map["error"])
        }
    }

    @Test
    fun `resolved tasks are deleted after some time`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
    fun `max reports for user consider only unresolved tasks`() {
        withTestApplication({ module(testing = true) }) {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            // NOTE: 'MAX_REPORTS_FOR_PRODUCT - 2' is because
            // product creation also creates a moderator task
            // and there are 2 products
            for (index in 0 until MAX_REPORTS_FOR_USER_TESTING - 2) {
                val barcode = if (index % 2 == 1) {
                    barcode1
                } else {
                    barcode2
                }
                map = authedGet(
                    clientToken, "/make_report/?barcode=${barcode}&text=text$index").jsonMap()
                assertEquals("ok", map["result"], map.toString())
            }

            // Error expected now
            map = authedGet(
                clientToken, "/make_report/?barcode=${barcode1}&text=finaltext1").jsonMap()
            assertEquals("too_many_reports_for_user", map["error"])

            // Resolve 1 task
            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            val taskId = (allTasks[0] as Map<*, *>)["id"]
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // Can make another report now
            map = authedGet(
                clientToken, "/make_report/?barcode=${barcode1}&text=finaltext1").jsonMap()
            assertEquals("ok", map["result"])
        }
    }

    @Test
    fun `max reports for product consider only unresolved tasks`() {
        withTestApplication({ module(testing = true) }) {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken1 = register()
            val clientToken2 = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken1, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            // NOTE: 'MAX_REPORTS_FOR_PRODUCT - 1' is because
            // product creation also creates a moderator task
            for (index in 0 until MAX_REPORTS_FOR_PRODUCT_TESTING - 1) {
                val clientToken = if (index % 2 == 1) {
                    clientToken1
                } else {
                    clientToken2
                }
                map = authedGet(
                    clientToken, "/make_report/?barcode=${barcode}&text=text$index").jsonMap()
                assertEquals("ok", map["result"], map.toString())
            }

            // Ensure there's a max number of tasks
            transaction {
                assertEquals(MAX_REPORTS_FOR_PRODUCT_TESTING, ModeratorTaskTable.selectAll().count().toInt())
            }

            // Error expected now
            map = authedGet(
                clientToken1, "/make_report/?barcode=${barcode}&text=finaltext1").jsonMap()
            assertEquals("too_many_reports_for_product", map["error"])

            // Resolve 1 task
            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            val taskId = (allTasks[0] as Map<*, *>)["id"]
            map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=$taskId").jsonMap()
            assertEquals("ok", map["result"])

            // Can make another report now
            map = authedGet(
                clientToken1, "/make_report/?barcode=${barcode}&text=finaltext1").jsonMap()
            assertEquals("ok", map["result"])
        }
    }

    @Test
    fun `can unresolve task`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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

    @Test
    fun `product veg statuses moderation`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("moderator", map["vegetarian_status_source"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("moderator", map["vegan_status_source"])
        }
    }

    @Test
    fun `product veg statuses moderation by simple user`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            map = authedGet(clientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `product veg statuses moderation with invalid veg statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "vegetarianStatus=POPOPOSITIVE&veganStatus=negative").jsonMap()
            assertEquals("invalid_veg_status", map["error"])

            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "vegetarianStatus=positive&veganStatus=NENENEGATIVE").jsonMap()
            assertEquals("invalid_veg_status", map["error"])
        }
    }

    @Test
    fun `product veg statuses moderation does not work with 1 param only`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            val moderatorClientToken = registerModerator()
            var resp = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&veganStatus=negative")
            assertEquals(404, resp.response.status()!!.value)

            resp = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&vegetarianStatus=negative")
            assertEquals(404, resp.response.status()!!.value)
        }
    }

    @Test
    fun `clear product veg statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/clear_product_veg_statuses/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["vegetarian_status"])
            assertEquals(null, map["vegetarian_status_source"])
            assertEquals(null, map["vegan_status"])
            assertEquals(null, map["vegan_status_source"])
        }
    }

    @Test
    fun `clear product veg statuses by simple user`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/clear_product_veg_statuses/?barcode=${barcode}").jsonMap()
            assertEquals("denied", map["error"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])
        }
    }

    @Test
    fun `specify product moderator choice reason`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "vegetarianStatus" to "positive",
                    "veganStatus" to "negative",
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["moderator_vegetarian_choice_reason"])
            assertEquals(null, map["moderator_vegetarian_sources_text"])
            assertEquals(null, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])

            // Specify reason without text
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "vegetarianChoiceReason" to "1",
                "veganChoiceReason" to "2",
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(1, map["moderator_vegetarian_choice_reason"])
            assertEquals(null, map["moderator_vegetarian_sources_text"])
            assertEquals(2, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])

            // Specify reason with text
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "vegetarianChoiceReason" to "3",
                "vegetarianSourcesText" to "Hello there!",
                "veganChoiceReason" to "4",
                "veganSourcesText" to "General Kenobi!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(3, map["moderator_vegetarian_choice_reason"])
            assertEquals("Hello there!", map["moderator_vegetarian_sources_text"])
            assertEquals(4, map["moderator_vegan_choice_reason"])
            assertEquals("General Kenobi!", map["moderator_vegan_sources_text"])

            // Clear reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["moderator_vegetarian_choice_reason"])
            assertEquals(null, map["moderator_vegetarian_sources_text"])
            assertEquals(null, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])
        }
    }

    @Test
    fun `new product moderator choice reason does not erase reasons of other products`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode1,
                    "vegetarianStatus" to "positive",
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode2,
                    "vegetarianStatus" to "positive",
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode1,
                "vegetarianChoiceReason" to "1",
                "vegetarianSourcesText" to "Hello",
                "veganChoiceReason" to "2",
                "veganSourcesText" to "there!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode2,
                "vegetarianChoiceReason" to "3",
                "vegetarianSourcesText" to "General Kenobi!",
                "veganChoiceReason" to "4",
                "veganSourcesText" to "You're a bold one!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify product 1 reason
            map = authedGet(clientToken, "/product_data/?barcode=${barcode1}").jsonMap()
            assertEquals(barcode1, map["barcode"])
            assertEquals(1, map["moderator_vegetarian_choice_reason"])
            assertEquals("Hello", map["moderator_vegetarian_sources_text"])
            assertEquals(2, map["moderator_vegan_choice_reason"])
            assertEquals("there!", map["moderator_vegan_sources_text"])

            // Verify product 2 reason
            map = authedGet(clientToken, "/product_data/?barcode=${barcode2}").jsonMap()
            assertEquals(barcode2, map["barcode"])
            assertEquals(3, map["moderator_vegetarian_choice_reason"])
            assertEquals("General Kenobi!", map["moderator_vegetarian_sources_text"])
            assertEquals(4, map["moderator_vegan_choice_reason"])
            assertEquals("You're a bold one!", map["moderator_vegan_sources_text"])
        }
    }

    @Test
    fun `specify product moderator choice reason by simple user`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "vegetarianStatus" to "positive",
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "vegetarianChoiceReason" to "1",
                "veganChoiceReason" to "2",
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `user deletion by content moderator`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModerator(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

            // At first can send a request by the user
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Try to delete user
            map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
            assertEquals("denied", map["error"])

            // The user still can do stuff
            map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])
        }
    }

    @Test
    fun `user deletion by everything-moderator`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModeratorOfEverything(id = moderatorId)
            val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()

            // At first can send a request by the user
            var map = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            // Delete user
            map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
            assertEquals("ok", map["result"])

            // Now the user cannot do anything
            val resp = authedGet(simpleUserClientToken, "/make_report/?barcode=123&text=someText").response
            assertNull(resp.content)
            assertEquals(401, resp.status()?.value)
        }
    }

    @Test
    fun `deletion of not existing user`() {
        withTestApplication({ module(testing = true) }) {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModeratorOfEverything(id = moderatorId)
            val simpleUserId = UUID.randomUUID()

            val map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
            assertEquals("user_not_found", map["error"])
        }
    }

    @Test
    fun `user deletion by simple user`() {
        withTestApplication({ module(testing = true) }) {
            val (simpleUserClientToken1, simpleUserId1) = registerAndGetTokenWithID()
            val (simpleUserClientToken2, simpleUserId2) = registerAndGetTokenWithID()

            var map = authedGet(simpleUserClientToken1, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(simpleUserClientToken2, "/delete_user/?userId=$simpleUserId1").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `moderator can reject a task`() {
        withTestApplication({ module(testing = true) }) {
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
        withTestApplication({ module(testing = true) }) {
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
    fun `moderator_task_data cmd`() {
        withTestApplication({ module(testing = true) }) {
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
    fun `product creation with multiple langs creates multiple moderation tasks with the langs`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                    "barcode" to barcode,
                    "vegetarianStatus" to "unknown",
                    "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // 2 tasks expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(2, allTasks.size, map.toString())

            // Verify langs
            val task1 = allTasks[0] as Map<*, *>
            val task2 = allTasks[1] as Map<*, *>
            assertTrue((task1["lang"] == "en" && task2["lang"] == "nl")
                    || (task1["lang"] == "nl" && task2["lang"] == "en"),
                map.toString())

            // Verify tasks have different ids
            assertNotEquals(task1["id"], task2["id"])

            // Erase unique data and compare tasks
            val task1LangErased = task1.toMutableMap()
            task1LangErased["lang"] = null
            task1LangErased["id"] = null
            val task2LangErased = task2.toMutableMap()
            task2LangErased["lang"] = null
            task2LangErased["id"] = null
            assertEquals(task1LangErased, task2LangErased, map.toString())
        }
    }

    @Test
    fun `product creation without a lang creates 1 moderation task without a lang`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown")).jsonMap()
            assertEquals("ok", map["result"])

            // 1 task expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val allTasks = map["tasks"] as List<*>
            assertEquals(1, allTasks.size, map.toString())

            // Verify lang
            val task = allTasks[0] as Map<*, *>
            assertNull(task["lang"])
        }
    }

    @Test
    fun `product update with new langs doesn't erase moderation tasks with unrelated langs`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Update the product
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "positive",
                "veganStatus" to "positive"),
                mapOf("langs" to listOf("ru", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // 3 tasks expected
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").jsonMap()
            val tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(3, tasks.size, map.toString())

            // Verify langs
            val langs = tasks.map { it["lang"] }
            assertTrue(langs.contains("en"), map.toString())
            assertTrue(langs.contains("ru"), map.toString())
            assertTrue(langs.contains("nl"), map.toString())
        }
    }

    @Test
    fun `count_moderator_tasks cmd`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Product 1
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "positive",
                "veganStatus" to "unknown")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "positive",
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
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/count_moderator_tasks/").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `all_moderator_tasks_data cmd with lang-related params`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var now = 1

            // Product 1
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "testingNow" to "${++now}"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "testingNow" to "${++now}"),
                mapOf("langs" to listOf("en", "ru"))).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "positive",
                "veganStatus" to "unknown",
                "testingNow" to "${++now}")).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 update
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "positive",
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
            assertEquals(barcode1, tasks[0]["barcode"])
            assertEquals(barcode2, tasks[1]["barcode"])
            assertEquals("en", tasks[0]["lang"])
            assertEquals("en", tasks[1]["lang"])

            // Ru
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "ru"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(2, tasks.size, map.toString())
            assertEquals(barcode2, tasks[0]["barcode"])
            assertEquals(barcode1, tasks[1]["barcode"])
            assertEquals("ru", tasks[0]["lang"])
            assertEquals("ru", tasks[1]["lang"])

            // Nl
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "nl"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            assertEquals(barcode1, tasks[0]["barcode"])
            assertEquals("nl", tasks[0]["lang"])

            // De
            map = authedGet(moderatorClientToken, "/all_moderator_tasks_data/", mapOf(
                "lang" to "de"
            )).jsonMap()
            tasks = (map["tasks"] as List<*>).map { it as Map<*, *> }
            assertEquals(1, tasks.size, map.toString())
            assertEquals(barcode1, tasks[0]["barcode"])
            assertEquals("de", tasks[0]["lang"])
        }
    }

    @Test
    fun `all_moderator_tasks_data cmd - provide both 'lang' and 'onlyWithNoLang'`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "vegetarianStatus" to "unknown",
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
    fun `random task assignment when moderator has known langs specified`() {
        withTestApplication({ module(testing = true) }) {
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
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "langs" to "en")).jsonMap()
            assertEquals("ok", map["result"])

            // Ru
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode1,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "langs" to "ru")).jsonMap()
            assertEquals("ok", map["result"])

            // De
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "vegetarianStatus" to "unknown",
                "veganStatus" to "unknown",
                "langs" to "de")).jsonMap()
            assertEquals("ok", map["result"])

            // No lang
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode2,
                "vegetarianStatus" to "unknown",
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
                map = authedGet(moderatorClientToken, "/resolve_moderator_task/?taskId=${tasks.last()["id"]}").jsonMap()
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
