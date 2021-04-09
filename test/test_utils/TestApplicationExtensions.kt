package vegancheckteam.untitled_vegan_app_server.test_utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationEngine.get(url: String, clientToken: String? = null): TestApplicationCall {
    return handleRequest(HttpMethod.Get, url) {
        clientToken?.let {
            addHeader("Authorization", "Bearer $it")
        }
    }
}

fun TestApplicationEngine.authedGet(token: String, url: String) = get(url, token)

fun TestApplicationResponse.jsonMap(): Map<*, *> {
    return ObjectMapper().readValue(content, MutableMap::class.java)
}

fun TestApplicationCall.jsonMap() = response.jsonMap()

fun TestApplicationEngine.register(): String {
    val response = get("/register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
    assertEquals(200, response.status()?.value)
    return response.jsonMap()["client_token"] as String
}

fun registerModerator(): String {
    val moderator = User(
        id = UUID.randomUUID(),
        loginGeneration = 1,
        googleId = null)
    transaction {
        UserTable.insert {
            it[id] = moderator.id
            it[loginGeneration] = moderator.loginGeneration
            it[name] = moderator.name
            it[googleId] = moderator.googleId
            it[userRightsGroup] = UserRightsGroup.MODERATOR.groupName
        }
    }
    return JwtController.makeToken(moderator, "device id")
}
