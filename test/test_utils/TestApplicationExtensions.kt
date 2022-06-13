package vegancheckteam.plante_server.test_utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
    queryParamsLists: Map<String, List<String>> = emptyMap(),
    body: String? = null,
    headers: Map<String, String> = emptyMap()) = request(HttpMethod.Get, url, clientToken, queryParams, queryParamsLists, body, headers)

fun TestApplicationEngine.request(
        httpMethod: HttpMethod,
        url: String,
        clientToken: String? = null,
        queryParams: Map<String, String> = emptyMap(),
        queryParamsLists: Map<String, List<String>> = emptyMap(),
        body: String? = null,
        headers: Map<String, String> = emptyMap()): TestApplicationCall {
    val queryParamsFinal = mutableListOf<Pair<String, String>>()
    for (keyValue in queryParams.entries) {
        queryParamsFinal.add(Pair(keyValue.key, keyValue.value))
    }
    for (keyValue in queryParamsLists) {
        for (value in keyValue.value) {
            queryParamsFinal.add(Pair(keyValue.key, value))
        }
    }
    val queryParamsStr = queryParamsFinal
        .map {
            val key = URLEncoder.encode(it.first, StandardCharsets.UTF_8)
            val value = URLEncoder.encode(it.second, StandardCharsets.UTF_8)
            "$key=$value"
        }
        .joinToString(separator = "&")
    val urlFinal = if (url.contains("?")) {
        "$url&$queryParamsStr"
    } else {
        "$url?$queryParamsStr"
    }
    return handleRequest(httpMethod, urlFinal) {
        clientToken?.let {
            addHeader("Authorization", "Bearer $it")
        }
        for (header in headers.entries) {
            addHeader(header.key, header.value)
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
    queryParamsLists: Map<String, List<String>> = emptyMap(),
    body: String? = null,
    headers: Map<String, String> = emptyMap()) = get(url, token, queryParams, queryParamsLists, body, headers)

fun TestApplicationResponse.jsonMap(): Map<*, *> {
    if (content == null) {
        throw IllegalStateException("Response content is null. HTTP status: ${status()}")
    }
    return ObjectMapper().readValue(content, MutableMap::class.java)
}

fun TestApplicationCall.jsonMap() = response.jsonMap()

fun TestApplicationEngine.register(name: String? = null): String {
    return registerAndGetTokenWithID(name = name).first
}

fun TestApplicationEngine.registerAndGetTokenWithID(name: String? = null): Pair<String, String> {
    var response = get("/login_or_register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
    assertEquals(200, response.status()?.value)
    val token = response.jsonMap()["client_token"] as String
    val id = response.jsonMap()["user_id"] as String

    if (name != null) {
        response = authedGet(token, "/update_user_data/?name=$name").response
        assertEquals(200, response.status()?.value)
    }

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
            it[userRightsGroup] = UserRightsGroup.CONTENT_MODERATOR.persistentCode
        }
    }
    return JwtController.makeToken(moderator, "device id")
}

fun registerModeratorOfEverything(id: UUID = UUID.randomUUID()): String {
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
            it[userRightsGroup] = UserRightsGroup.EVERYTHING_MODERATOR.persistentCode
        }
    }
    return JwtController.makeToken(moderator, "device id")
}
