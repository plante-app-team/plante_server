package vegancheckteam.untitled_vegan_app_server

import io.ktor.server.testing.withTestApplication
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskType
import vegancheckteam.untitled_vegan_app_server.responses.MAX_REPORTS_FOR_PRODUCT_TESTING
import vegancheckteam.untitled_vegan_app_server.responses.MAX_REPORTS_FOR_USER_TESTING
import vegancheckteam.untitled_vegan_app_server.responses.REPORT_TEXT_MAX_LENGTH
import vegancheckteam.untitled_vegan_app_server.responses.REPORT_TEXT_MIN_LENGTH
import vegancheckteam.untitled_vegan_app_server.test_utils.authedGet
import vegancheckteam.untitled_vegan_app_server.test_utils.jsonMap
import vegancheckteam.untitled_vegan_app_server.test_utils.register
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModerationRequestsTest {
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
}
