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
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.registerModeratorOfEverything
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class UserAvatarCmdsTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            runBlocking {
                for (key in S3.listKeys(prefix = "").toList()) {
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

                var userData = authedGet(token, "/user_data/").jsonMap()
                assertNull(userData["avatar_id"])

                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                assertTrue(img.exists())

                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()
                assertNotNull(UUID.fromString(avatarId), message = avatarId)

                resp = authedGet(token, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(img.readBytes(), respAvatar))

                userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(avatarId, userData["avatar_id"])
            }
        }
    }

    @Test
    fun `get not existing avatar`() {
        withPlanteTestApplication {
            runBlocking {
                val (token, userId) = registerAndGetTokenWithID()
                val notExistingUser = UUID.randomUUID()
                val notExistingAvatarId = UUID.randomUUID().toString()

                // Not existing user
                var resp = authedGet(token, "/user_avatar_data/$notExistingUser/$notExistingAvatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())

                // Existing user, not existing avatar
                resp = authedGet(token, "/user_avatar_data/$userId/$notExistingAvatarId")
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

                val respJson = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(img.readBytes())
                }.jsonMap()
                val avatarId = respJson["result"].toString()

                // Avatar found
                var resp = authedGet(token, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // DB knows of the avatar existence
                var userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(avatarId, userData["avatar_id"])

                // Manually delete the avatar
                val user = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }
                S3.deleteData(userAvatarPathS3(user.id.toString(), avatarId))

                // DB still thinks the avatar exists
                userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(avatarId, userData["avatar_id"])
                // Avatar not found though
                resp = authedGet(token, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // DB now understands there's no avatar
                userData = authedGet(token, "/user_data/").jsonMap()
                assertNull(userData["avatar_id"])
            }
        }
    }

    @Test
    fun `requests with invalid avatar ID do not delete valid avatars`() {
        withPlanteTestApplication {
            runBlocking {
                val (token, userId) = registerAndGetTokenWithID()

                var userData = authedGet(token, "/user_data/").jsonMap()
                assertNull(userData["avatar_id"])

                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                assertTrue(img.exists())

                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()
                assertNotNull(UUID.fromString(avatarId), message = avatarId)

                // Request invalid avatar
                val notExistingAvatarId = UUID.randomUUID().toString()
                resp = authedGet(token, "/user_avatar_data/$userId/$notExistingAvatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())

                // Valid avatar exists though
                resp = authedGet(token, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(img.readBytes(), respAvatar))
                userData = authedGet(token, "/user_data/").jsonMap()
                assertEquals(avatarId, userData["avatar_id"])
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

                // No avatar
                var user = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }
                assertNull(user.avatarId)

                // Not too large anymore!
                val notTooLargeData = "A".repeat(USER_AVATAR_MAX_SIZE)
                resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $token")
                    setBody(notTooLargeData.toByteArray())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"]?.toString()

                // Avatar exists
                resp = authedGet(token, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(notTooLargeData.toByteArray(), respAvatar))
                // DB knows of the avatar as well
                user = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }
                assertEquals(avatarId, user.avatarId.toString())
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
                val avatarId = resp.jsonMap()["result"]

                // User2 requests avatar of User1
                resp = authedGet(token2, "/user_avatar_data/$userId1/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val respAvatar = resp.response.byteContent
                assertTrue(Arrays.equals(img.readBytes(), respAvatar))

                // User2 requests their avatar with the avatar ID of the first user
                resp = authedGet(token2, "/user_avatar_data/$userId2/$avatarId")
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

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $simpleUserClientToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()

                // Ensure the user has the avatar
                resp = authedGet(moderatorClientToken, "/user_avatar_data/$simpleUserId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(simpleUserId, avatarId)))

                // Delete user
                val map = authedGet(moderatorClientToken, "/delete_user/?userId=$simpleUserId").jsonMap()
                assertEquals("ok", map["result"])

                // Ensure the user does not have the avatar anymore
                resp = authedGet(moderatorClientToken, "/user_avatar_data/$simpleUserId/$avatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // ...S3 doesn't have the file, too
                assertNull(S3.getData(userAvatarPathS3(simpleUserId, avatarId)))
            }
        }
    }

    @Test
    fun `user can delete their own avatar`() {
        withPlanteTestApplication {
            runBlocking {
                val (clientToken, userId) = registerAndGetTokenWithID()
                val simpleUser = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $clientToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()

                // Ensure the user has the avatar
                resp = authedGet(clientToken, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(simpleUser.id.toString(), avatarId)))

                // Delete avatar
                val map = authedGet(clientToken, "/user_avatar_delete/").jsonMap()
                assertEquals("ok", map["result"])

                // Ensure the user does not have the avatar anymore
                resp = authedGet(clientToken, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // ...S3 doesn't have the file, too
                assertNull(S3.getData(userAvatarPathS3(simpleUser.id.toString(), avatarId)))
            }
        }
    }

    @Test
    fun `user cannot delete avatar of another user`() {
        withPlanteTestApplication {
            runBlocking {
                val (userToken1, userId1) = registerAndGetTokenWithID()
                val (userToken2, _) = registerAndGetTokenWithID()
                val user1 = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId1))
                        .map { User.from(it) }
                        .first()
                }

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $userToken1")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()

                // Ensure the user has the avatar
                resp = authedGet(userToken2, "/user_avatar_data/$userId1/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(user1.id.toString(), avatarId)))

                // Try to delete avatar using token of another user
                val map = authedGet(userToken2, "/user_avatar_delete/", mapOf(
                    "userId" to userId1
                )).jsonMap()
                assertEquals("denied", map["error"])

                // Ensure the user still has the avatar
                resp = authedGet(userToken2, "/user_avatar_data/$userId1/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(user1.id.toString(), avatarId)))
            }
        }
    }

    @Test
    fun `moderator can delete avatar of another user`() {
        withPlanteTestApplication {
            runBlocking {
                val (userToken, userId) = registerAndGetTokenWithID()
                val moderatorId = UUID.randomUUID()
                val moderatorClientToken = registerModerator(moderatorId)
                val user = transaction {
                    UserTable
                        .select(UserTable.id eq UUID.fromString(userId))
                        .map { User.from(it) }
                        .first()
                }

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $userToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId = resp.jsonMap()["result"].toString()

                // Ensure the user has the avatar
                resp = authedGet(userToken, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(user.id.toString(), avatarId)))

                // Delete avatar using token of the moderator
                val map = authedGet(moderatorClientToken, "/user_avatar_delete/", mapOf(
                    "userId" to userId
                )).jsonMap()
                assertEquals("ok", map["result"])

                // Ensure the user does not have the avatar anymore
                resp = authedGet(userToken, "/user_avatar_data/$userId/$avatarId")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // ...S3 doesn't have the file, too
                assertNull(S3.getData(userAvatarPathS3(user.id.toString(), avatarId)))
            }
        }
    }

    @Test
    fun `when new avatar is uploaded old avatar is deleted`() {
        withPlanteTestApplication {
            runBlocking {
                val (clientToken, userId) = registerAndGetTokenWithID()

                // Upload user avatar
                val img = File("./assets_for_tests/front_coca_light_de.jpg")
                var resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $clientToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId1 = resp.jsonMap()["result"].toString()

                // Ensure the user has the avatar
                resp = authedGet(clientToken, "/user_avatar_data/$userId/$avatarId1")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(userId, avatarId1)))

                // Upload a new avatar
                resp = handleRequest(HttpMethod.Post, "/user_avatar_upload/") {
                    addHeader("Authorization", "Bearer $clientToken")
                    setBody(img.readBytes())
                }
                assertEquals(HttpStatusCode.OK, resp.response.status())
                val avatarId2 = resp.jsonMap()["result"].toString()

                // Ensure the user has the second avatar
                resp = authedGet(clientToken, "/user_avatar_data/$userId/$avatarId2")
                assertEquals(HttpStatusCode.OK, resp.response.status())
                // ...S3 has the file, too
                assertNotNull(S3.getData(userAvatarPathS3(userId, avatarId2)))

                // Ensure the user does not have the first avatar anymore
                resp = authedGet(clientToken, "/user_avatar_data/$userId/$avatarId1")
                assertEquals(HttpStatusCode.NotFound, resp.response.status())
                // ...S3 doesn't have the file, too
                assertNull(S3.getData(userAvatarPathS3(userId, avatarId1)))
            }
        }
    }
}
