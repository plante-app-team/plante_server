package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.registerModeratorOfEverything
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_UserModeration_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `user deletion by content moderator`() {
        withPlanteTestApplication {
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
        withPlanteTestApplication {
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
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderatorClientToken = registerModeratorOfEverything(id = moderatorId)
            val simpleUserId = UUID.randomUUID()

            val map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
            assertEquals("user_not_found", map["error"])
        }
    }

    @Test
    fun `user deletion by simple user`() {
        withPlanteTestApplication {
            val (simpleUserClientToken1, simpleUserId1) = registerAndGetTokenWithID()
            val (simpleUserClientToken2, _) = registerAndGetTokenWithID()

            var map = authedGet(simpleUserClientToken1, "/make_report/?barcode=123&text=someText").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(simpleUserClientToken2, "/delete_user/?userId=$simpleUserId1").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
