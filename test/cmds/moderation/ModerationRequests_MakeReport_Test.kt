package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.cmds.MAX_REPORTS_FOR_PRODUCT_TESTING
import vegancheckteam.plante_server.cmds.MAX_REPORTS_FOR_USER_TESTING
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MAX_LENGTH
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MIN_LENGTH
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_MakeReport_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `make_report command`() {
        withPlanteTestApplication {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
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
                assertTrue("text1" in texts)
                assertTrue("text2" in texts)
                assertTrue("text3" in texts)
            }
        }
    }

    @Test
    fun `make_report max reports for user`() {
        withPlanteTestApplication {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&veganStatus=unknown").jsonMap()
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
    fun `make_report max reports for product`() {
        withPlanteTestApplication {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken1 = register()
            val clientToken2 = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken1, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
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
    fun `make_report command min and max text lengths`() {
        withPlanteTestApplication {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
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
    fun `max reports for user consider only unresolved tasks`() {
        withPlanteTestApplication {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken = register()

            val barcode1 = UUID.randomUUID().toString()
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode1}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])
            val barcode2 = UUID.randomUUID().toString()
            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode2}&veganStatus=unknown").jsonMap()
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
        withPlanteTestApplication {
            // Set up
            transaction {
                ModeratorTaskTable.deleteAll()
            }

            val clientToken1 = register()
            val clientToken2 = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(clientToken1, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
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
}