package vegancheckteam.untitled_vegan_app_server

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            var response = handleRequest(HttpMethod.Get, "/is_registered/rob").response
            assertEquals("nope", response.content)

            response = handleRequest(HttpMethod.Get, "/register_user/rob").response
            assertEquals("ok", response.content)

            response = handleRequest(HttpMethod.Get, "/is_registered/rob").response
            assertEquals("yep", response.content)

            response = handleRequest(HttpMethod.Get, "/delete_user/rob").response
            assertEquals("done!", response.content)

            response = handleRequest(HttpMethod.Get, "/is_registered/rob").response
            assertEquals("nope", response.content)
        }
    }

    @Test
    fun testClientMock() {
        runBlocking {
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request -> 
                        when (request.url.fullPath) {
                            "/" -> respond(
                                ByteReadChannel(byteArrayOf(1, 2, 3)),
                                headers = headersOf("X-MyHeader", "MyValue")
                            )
                            else -> respond("Not Found ${request.url.encodedPath}", HttpStatusCode.NotFound)
                        }
                    }
                }
                expectSuccess = false
            }
            assertEquals(byteArrayOf(1, 2, 3).toList(), client.get<ByteArray>("/").toList())
            assertEquals("MyValue", client.request<HttpResponse>("/").headers["X-MyHeader"])
            assertEquals("Not Found other/path", client.get<String>("/other/path"))
        }
    }
}
