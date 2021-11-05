package vegancheckteam.plante_server.cmds.moderation

import io.ktor.server.testing.TestApplicationCall
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_CreateUpdateProduct_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    private fun productCreationAndChangeCreatesModeratorTaskTest(
            createCmdEnd: String,
            updateCmdEnd: String,
            expectedTaskType: ModeratorTaskType) {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // No moderator task yet
            var allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(0, allTasks.size, allTasks.toString())

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}$createCmdEnd").jsonMap()
            assertEquals("ok", map["result"])

            // Now there is a task
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, map.toString())
            assertEquals(expectedTaskType.taskName, allTasks.first()["task_type"])

            // Clear
            transaction {
                ModeratorTaskTable.deleteWhere {
                    ModeratorTaskTable.productBarcode eq barcode
                }
            }

            // Update the product
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}$updateCmdEnd").jsonMap()
            assertEquals("ok", map["result"])

            // Now there is a task again
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, map.toString())
            assertEquals(expectedTaskType.taskName, allTasks.first()["task_type"])
        }
    }

    @Test
    fun `product creation and change with vegan status create moderator tasks`() {
        productCreationAndChangeCreatesModeratorTaskTest(
            "&veganStatus=unknown",
            "&veganStatus=positive",
            ModeratorTaskType.PRODUCT_CHANGE,
        )
    }

    @Test
    fun `product creation and change without vegan status create moderator tasks`() {
        productCreationAndChangeCreatesModeratorTaskTest(
            "",
            "",
            ModeratorTaskType.PRODUCT_CHANGE_IN_OFF,
        )
    }

    @Test
    fun `there's always no more than 1 moderator task of PRODUCT_CHANGE type`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            // Update the product
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            // Expecting 1 task only
            val moderatorClientToken = registerModerator()
            val allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, map.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE.taskName, allTasks.first()["task_type"])
        }
    }

    @Test
    fun `several different products can have their own PRODUCT_CHANGE moderator tasks`() {
        withPlanteTestApplication {
            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Create product1
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            // Create product2
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&veganStatus=positive").jsonMap()
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
    fun `product creation with multiple langs creates multiple moderation tasks with the langs`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
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
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
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
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product
            var map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "veganStatus" to "unknown"),
                mapOf("langs" to listOf("en", "nl"))).jsonMap()
            assertEquals("ok", map["result"])

            // Update the product
            map = authedGet(clientToken, "/create_update_product/?", mapOf(
                "barcode" to barcode,
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
    fun `if product updated with changes while PRODUCT_CHANGE_IN_OFF task exist, it gets deleted`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product without a veg status
            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])
            // Expected task
            var allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.taskName, allTasks[0]["task_type"])

            // Update the product with a veg status
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            // Still expecting 1 task, but this time with a different type
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE.taskName, allTasks[0]["task_type"])
        }
    }

    @Test
    fun `if product updated with changes while PRODUCT_CHANGE task exist, tasks don't change`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product with a veg status
            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}&veganStatus=positive").jsonMap()
            assertEquals("ok", map["result"])
            // Expected task
            var allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE.taskName, allTasks[0]["task_type"])
            val initialTask = allTasks[0]

            // Update the product with another veg status
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            // Still expecting 1 task, expecting it to be exactly same as the initial one
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            val finalTask = allTasks[0]
            assertEquals(initialTask, finalTask)
        }
    }

    @Test
    fun `if product updated without changes and PRODUCT_CHANGE_IN_OFF task exist, tasks don't change`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product without a veg status
            var map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])
            // Expected task
            var allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.taskName, allTasks[0]["task_type"])
            val initialTask = allTasks[0]

            // Update the product with not veg status, again
            map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])

            // Still expecting 1 task, expecting it to be exactly same as the initial one
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            val finalTask = allTasks[0]
            assertEquals(initialTask, finalTask)
        }
    }

    @Test
    fun `if product updated without changes and PRODUCT_CHANGE task exist, tasks don't change`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            // Create a product with a veg status
            var map = authedGet(clientToken, "/create_update_product/?" +
                "barcode=${barcode}&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])
            // Expected task
            var allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            assertEquals(ModeratorTaskType.PRODUCT_CHANGE.taskName, allTasks[0]["task_type"])
            val initialTask = allTasks[0]

            // Update the product without any veg status
            map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])

            // Still expecting 1 task, expecting it to be exactly same as the initial one
            allTasks = authedGet(moderatorClientToken, "/all_moderator_tasks_data/").allModeratorTasks()
            assertEquals(1, allTasks.size, allTasks.toString())
            val finalTask = allTasks[0]
            assertEquals(initialTask, finalTask)
        }
    }
}

fun TestApplicationCall.allModeratorTasks(): List<Map<*, *>> {
    val map = response.jsonMap()
    return (map["tasks"] as List<*>).map { it as Map<*, *> }
}