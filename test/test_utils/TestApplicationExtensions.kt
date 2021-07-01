package vegancheckteam.plante_server.test_utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.request.contentType
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationEngine.get(
        url: String,
        clientToken: String? = null,
        queryParams: Map<String, String> = emptyMap(),
        body: String? = null): TestApplicationCall {
    val queryParamsStr = queryParams
        .map {
            val key = URLEncoder.encode(it.key, StandardCharsets.UTF_8)
            val value = URLEncoder.encode(it.value, StandardCharsets.UTF_8)
            "$key=$value"
        }
        .joinToString(separator = "&")
    val urlFinal = if (url.contains("?")) {
        "$url&$queryParamsStr"
    } else {
        "$url?$queryParamsStr"
    }
    return handleRequest(HttpMethod.Get, urlFinal) {
        clientToken?.let {
            addHeader("Authorization", "Bearer $it")
        }
        body?.let {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(it)
        }
    }
}

fun TestApplicationEngine.authedGet(
    token: String,
    url: String,
    queryParams: Map<String, String> = emptyMap(),
    body: String? = null) = get(url, token, queryParams, body)

fun TestApplicationResponse.jsonMap(): Map<*, *> {
    return ObjectMapper().readValue(content, MutableMap::class.java)
}

fun TestApplicationCall.jsonMap() = response.jsonMap()

fun TestApplicationEngine.register(): String {
    return registerAndGetTokenWithID().first
}

fun TestApplicationEngine.registerAndGetTokenWithID(): Pair<String, String> {
    val response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
    assertEquals(200, response.status()?.value)
    val token = response.jsonMap()["client_token"] as String
    val id = response.jsonMap()["user_id"] as String
    return Pair(token, id)
}

fun registerModerator(id: UUID = UUID.randomUUID()): String {
    val moderator = User(
        id = id,
        loginGeneration = 1,
        googleId = null)
    transaction {
        UserTable.insert {
            it[UserTable.id] = moderator.id
            it[loginGeneration] = moderator.loginGeneration
            it[creationTime] = ZonedDateTime.now().toEpochSecond()
            it[name] = moderator.name
            it[googleId] = moderator.googleId
            it[userRightsGroup] = UserRightsGroup.MODERATOR.persistentCode
        }
    }
    return JwtController.makeToken(moderator, "device id")
}
