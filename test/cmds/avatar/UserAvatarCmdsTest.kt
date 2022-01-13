package vegancheckteam.plante_server.cmds.avatar

import cmds.avatar.USER_AVATAR_MAX_SIZE
import cmds.avatar.userAvatarPathS3
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.aws.S3
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModeratorOfEverything
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class UserAvatarCmdsTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            runBlocking {
                for (key in S3.listKeys().toList()) {
                    S3.deleteData(key)
                }
            }
        }
    }

    @Test
    fun `set and get an avatar`() {
        withPlanteTestApplication {
            runBlocking {
                val (token, userId) = registerAndGetTokenWithID()

                var resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())

                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                assertTrue(img.exists())

                resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())

                resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(img.readBytes(), respAvatar))
            }
        }
    }

    @Test
    fun `get avatar for not existing user`() {
        withPlanteTestApplication {
            runBlocking {
                val token = register()
                val notExistingUser = UUID.randomUUID()
                val resp = authedGet(token, "/user_avatar_data/$notExistingUser")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
            }
        }
    }

    @Test
    fun `get avatar for user after avatar manually deleted from S3`() {
        withPlanteTestApplication {
            runBlocking {
                val (token, userId) = registerAndGetTokenWithID()

                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                assertTrue(img.exists())

                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())

                // Avatar found
                resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // DB knows of the avatar existence
                var userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(userData["has_avatar"], true)

                // Manually delete the product
                val user = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }
                S3.deleteData(userAvatarPathS3(user))

                // DB still thinks the avatar exists
                userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(userData["has_avatar"], true)
                // Avatar not found though
                resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // DB now understands there's no avatar
                userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(userData["has_avatar"], false)
            }
        }
    }

    @Test
    fun `avatar max size`() {
        withPlanteTestApplication {
            runBlocking {
                val (token, userId) = registerAndGetTokenWithID()

                // Too large!
                val tooLargeData = "A".repeat(USER_AVATAR_MAX_SIZE + 1)
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(tooLargeData.toByteArray())
                }
                assertEquals(HttpStatusCode.PayloadTooLarge, resp.response.status())

                resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())

                // Not too large anymore!
                val notTooLargeData = "A".repeat(USER_AVATAR_MAX_SIZE)
                resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(notTooLargeData.toByteArray())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())

                resp = authedGet(token, "/user_avatar_data/$userId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(notTooLargeData.toByteArray(), respAvatar))
            }
        }
    }

    @Test
    fun `a user can get avatar of another user`() {
        withPlanteTestApplication {
            runBlocking {
                val (token1, userId1) = registerAndGetTokenWithID()
                val (token2, userId2) = registerAndGetTokenWithID()

                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                assertTrue(img.exists())

                // User1 has an avatar
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token1")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())

                // User2 requests avatar of User1
                resp = authedGet(token2, "/user_avatar_data/$userId1")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(img.readBytes(), respAvatar))

                // User2 requests their own avatar
                resp = authedGet(token2, "/user_avatar_data/$userId2")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
            }
        }
    }

    @Test
    fun `user deletion deletes their avatar from S3`() {
        withPlanteTestApplication {
            runBlocking {
                val moderatorId = UUID.randomUUID()
                val moderatorClientToken = registerModeratorOfEverything(id = moderatorId)
                val (simpleUserClientToken, simpleUserId) = registerAndGetTokenWithID()
                val simpleUser = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(simpleUserId))
                        .map { User.from(it) }
                        .first()
                }

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $simpleUserClientToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())

                // Ensure the user has the avatar
                resp = authedGet(moderatorClientToken, "/user_avatar_data/$simpleUserId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(simpleUser)))

                // Delete user
                val map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
                assertEquals("ok", map["result"])

                // Ensure the user does not have the avatar anymore
                resp = authedGet(moderatorClientToken, "/user_avatar_data/$simpleUserId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // ...S3 doesn't have the file, too
                assertNull(S3.getData(userAvatarPathS3(simpleUser)))
            }
        }
    }
}
