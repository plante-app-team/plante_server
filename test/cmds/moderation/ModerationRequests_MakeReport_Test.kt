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
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MAX_LENGTH
import vegancheckteam.plante_server.cmds.REPORT_TEXT_MIN_LENGTH
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.allModeratorTasksCmd
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.makeReportCmd
import vegancheckteam.plante_server.test_utils.putProductToShopCmd
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.requestNewsCmd
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_MakeReport_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
                NewsPieceProductAtShopTable.deleteAll()
                NewsPieceTable.deleteAll()
            }
        }
    }

    @Test
    fun `make_report command with barcode`() {
        withPlanteTestApplication {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            val map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            makeReportCmd(clientToken, "text1", barcode = barcode)
            makeReportCmd(clientToken, "text2", barcode = barcode)
            makeReportCmd(clientToken, "text3", barcode = barcode)

            val moderatorClientToken = registerModerator()
            val tasks = allModeratorTasksCmd(moderatorClientToken)
                .filter { it["task_type"] == ModeratorTaskType.USER_PRODUCT_REPORT.typeName }
            val texts = tasks.map { it["text_from_user"] }
            assertTrue("text1" in texts)
            assertTrue("text2" in texts)
            assertTrue("text3" in texts)
        }
    }

    @Test
    fun `make_report command with news piece ID`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(clientToken, barcode, shop, lat = 1.0, lon = 1.0, now = 123)

            val news = requestNewsCmd(clientToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())
            val newsPieceId = (news[0]["id"] as Int)

            makeReportCmd(clientToken, "text1", newsPieceID = newsPieceId)

            val moderatorClientToken = registerModerator()
            val tasks = allModeratorTasksCmd(moderatorClientToken)
                .filter { it["task_type"] == ModeratorTaskType.USER_NEWS_PIECE_REPORT.typeName }

            assertEquals(1, tasks.size)
            assertEquals(newsPieceId, tasks[0]["news_piece_id"])
            assertEquals("text1", tasks[0]["text_from_user"])
        }
    }

    @Test
    fun `make_report command with invalid news piece ID`() {
        withPlanteTestApplication {
            val clientToken = register()

            val invalidNewsPieceId = 999999

            makeReportCmd(clientToken, "text1", newsPieceID = invalidNewsPieceId, expectedError = "news_piece_not_found")

            val moderatorClientToken = registerModerator()
            val tasks = allModeratorTasksCmd(moderatorClientToken)
                .filter { it["task_type"] == ModeratorTaskType.USER_NEWS_PIECE_REPORT.typeName }
            assertEquals(0, tasks.count())
        }
    }

    @Test
    fun `make_report command min and max text lengths`() {
        withPlanteTestApplication {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            val map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            val text1 = "a".repeat(REPORT_TEXT_MIN_LENGTH - 1)
            makeReportCmd(clientToken, text1, barcode = barcode, expectedError = "report_text_too_short")

            val text2 = "a".repeat(REPORT_TEXT_MAX_LENGTH + 1)
            makeReportCmd(clientToken, text2, barcode = barcode, expectedError = "report_text_too_long")

            // No reports should be created
            val moderatorClientToken = registerModerator()
            val tasks = allModeratorTasksCmd(moderatorClientToken)
                .filter { it["task_type"] == ModeratorTaskType.USER_PRODUCT_REPORT.typeName }
            assertEquals(0, tasks.size)
        }
    }
}