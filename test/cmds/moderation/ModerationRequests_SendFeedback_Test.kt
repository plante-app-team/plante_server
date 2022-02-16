package vegancheckteam.plante_server.cmds.moderation

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.cmds.FEEDBACK_MAX_LENGTH
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_SendFeedback_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `send_feedback command`() {
        withPlanteTestApplication {
            val clientToken = register()

            var map = authedGet(clientToken, "/send_feedback/?text=text1").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/send_feedback/?text=text2").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/send_feedback/?text=text3").jsonMap()
            assertEquals("ok", map["result"])

            transaction {
                val tasks = ModeratorTaskTable.select {
                    ModeratorTaskTable.taskType eq ModeratorTaskType.USER_FEEDBACK.persistentCode
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
    fun `send_feedback command max text length`() {
        withPlanteTestApplication {
            val clientToken = register()

            val text = "a".repeat(FEEDBACK_MAX_LENGTH + 1)
            val map = authedGet(clientToken, "/send_feedback/?text=$text").jsonMap()
            assertEquals("feedback_too_long", map["error"])

            // No reports should be created
            transaction {
                val tasks = ModeratorTaskTable.select {
                    ModeratorTaskTable.taskType eq ModeratorTaskType.USER_REPORT.persistentCode
                }
                assertEquals(0, tasks.count())
            }
        }
    }
}