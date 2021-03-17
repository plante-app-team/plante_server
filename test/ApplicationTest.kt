package vegancheckteam.untitled_vegan_app_server

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.http.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.logging.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("HELLO WORLD!", response.content)
            }
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
