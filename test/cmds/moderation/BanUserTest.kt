package vegancheckteam.plante_server.cmds.moderation

import kotlin.test.assertEquals
import org.junit.Test
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.banUserCmd
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class BanUserTest {
    @Test
    fun `ban and unban user`() {
        withPlanteTestApplication {
            val (clientToken, userId) = registerAndGetTokenWithID(name = "Bob")

            var map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])

            val moderatorToken = registerModerator()
            banUserCmd(moderatorToken, userId)

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("banned", map["error"])

            banUserCmd(moderatorToken, userId, unban = true)

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
        }
    }

    @Test
    fun `ban user by a normal user`() {
        withPlanteTestApplication {
            val (clientToken, userId) = registerAndGetTokenWithID(name = "Bob")

            var map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])

            val anotherUserToken = register()
            banUserCmd(anotherUserToken, userId, expectedError = "denied")

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])
        }
    }

    @Test
    fun `unban user by a normal user`() {
        withPlanteTestApplication {
            val (clientToken, userId) = registerAndGetTokenWithID(name = "Bob")

            var map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("Bob", map["name"])

            val moderatorToken = registerModerator()
            banUserCmd(moderatorToken, userId)

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("banned", map["error"])

            val anotherUserToken = register()
            banUserCmd(anotherUserToken, userId, unban = true, expectedError = "denied")

            map = authedGet(clientToken, "/user_data/").jsonMap()
            assertEquals("banned", map["error"])
        }
    }
}
