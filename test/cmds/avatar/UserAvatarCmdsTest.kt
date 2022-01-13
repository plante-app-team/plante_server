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
}
