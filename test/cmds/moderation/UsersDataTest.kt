package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class UsersDataTest {
    @Before
    fun setUp() {
    }

    @Test
    fun `users_data general test`() {
        withPlanteTestApplication {
            val (_, user1) = registerAndGetTokenWithID()
            val (_, _) = registerAndGetTokenWithID()
            val (_, user3) = registerAndGetTokenWithID()
            val moderator1 = UUID.randomUUID()
            val moderatorClientToken = registerModerator(moderator1)
            val moderator2 = UUID.randomUUID()
            registerModerator(moderator2)

            val randomUidNotID = UUID.randomUUID().toString()

            val map = authedGet(moderatorClientToken, "/users_data/", queryParamsLists = mapOf(
                "ids" to listOf(user1, user3, moderator2.toString(), randomUidNotID)
            )).jsonMap()
            val result = (map["result"] as List<*>).map { it as Map<*, *> }
            assertEquals(3, result.size, map.toString())
            val ids = result.map { it["user_id"] }.toSet()
            assertEquals(setOf(user1, user3, moderator2.toString()), ids.toSet())
        }
    }

    @Test
    fun `users_data cannot be obtained by normal user`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/users_data/", queryParamsLists = mapOf(
                "ids" to listOf(user)
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
