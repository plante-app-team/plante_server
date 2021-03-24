package vegancheckteam.untitled_vegan_app_server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


class UserRequestsTest {
    var mapper = ObjectMapper()

    @Test
    fun registerUpdateGetUser() {
        withTestApplication({ module(testing = true) }) {
            var response = handleRequest(HttpMethod.Get, "/register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            var map = mapper.readValue(response.content, MutableMap::class.java)
            val id = map["user_id"] as String
            val clientToken = map["client_token"] as String
            assertFalse(id.isEmpty())
            assertFalse(clientToken.isEmpty())

            response = handleRequest(HttpMethod.Get, "/update_user_data/?newName=Bob") {
                addHeader("Authorization", "Bearer $clientToken")
            }.response
            map = mapper.readValue(response.content, MutableMap::class.java)
            assertEquals(map["result"], "ok")

            response = handleRequest(HttpMethod.Get, "/user_data/") {
                addHeader("Authorization", "Bearer $clientToken")
            }.response
            map = mapper.readValue(response.content, MutableMap::class.java)
            assertEquals(map["user_id"], id)
            assertEquals(map["name"], "Bob")
        }
    }

    @Test
    fun unauthorizedUpdate() {
        withTestApplication({ module(testing = true) }) {
            var response = handleRequest(HttpMethod.Get, "/register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").response
            assertEquals(200, response.status()?.value)

            // NOTE: token is not passed
            response = handleRequest(HttpMethod.Get, "/update_user_data/?newName=Bob").response
            assertNull(response.content)
            assertEquals(401, response.status()?.value)
        }
    }

    @Test
    fun canLoginSecondTime() {
        withTestApplication({ module(testing = true) }) {
            val googleId = UUID.randomUUID()
            var response = handleRequest(HttpMethod.Get, "/register_user/?deviceId=1&googleIdToken=$googleId").response
            var map = mapper.readValue(response.content, MutableMap::class.java)
            val id1 = map["user_id"] as String
            val clientToken1 = map["client_token"] as String

            response = handleRequest(HttpMethod.Get, "/login_user/?deviceId=2&googleIdToken=$googleId").response
            map = mapper.readValue(response.content, MutableMap::class.java)
            val id2 = map["user_id"] as String
            val clientToken2 = map["client_token"] as String

            assertEquals(id1, id2)
            assertNotEquals(clientToken1, clientToken2)

            // Both token expected to be valid

            val content1 = handleRequest(HttpMethod.Get, "/user_data/") {
                addHeader("Authorization", "Bearer $clientToken1")
            }.response.content
            val content2 = handleRequest(HttpMethod.Get, "/user_data/") {
                addHeader("Authorization", "Bearer $clientToken2")
            }.response.content

            assertEquals(content1, content2)
            map = mapper.readValue(content1, MutableMap::class.java)
            assertEquals(id1, map["user_id"])
        }
    }

    @Test
    fun signOutAll() {
        withTestApplication({ module(testing = true) }) {
            val googleId = UUID.randomUUID()
            var response = handleRequest(HttpMethod.Get, "/register_user/?deviceId=123&googleIdToken=$googleId").response
            assertEquals(200, response.status()?.value)

            var map = mapper.readValue(response.content, MutableMap::class.java)
            val clientToken = map["client_token"] as String

            response = handleRequest(HttpMethod.Get, "/sign_out_all/") {
                addHeader("Authorization", "Bearer $clientToken")
            }.response
            map = mapper.readValue(response.content, MutableMap::class.java)
            assertEquals(map["result"], "ok")

            // NOTE: token is passed but auth is still expected to fail
            response = handleRequest(HttpMethod.Get, "/update_user_data/?newName=Bob") {
                addHeader("Authorization", "Bearer $clientToken")
            }.response
            assertNull(response.content)
            assertEquals(401, response.status()?.value)

            // Login again
            response = handleRequest(HttpMethod.Get, "/login_user/?deviceId=2&googleIdToken=$googleId").response
            map = mapper.readValue(response.content, MutableMap::class.java)
            val clientToken2 = map["client_token"] as String

            response = handleRequest(HttpMethod.Get, "/update_user_data/?newName=Bob") {
                addHeader("Authorization", "Bearer $clientToken2")
            }.response
            assertEquals(200, response.status()?.value)
        }
    }
}
