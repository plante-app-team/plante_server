package vegancheckteam.untitled_vegan_app_server.auth

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.features.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.lang.Long.max
import kotlinx.coroutines.runBlocking

class CioHttpTransport(private val httpClient: HttpClient) : HttpTransport() {
    override fun buildRequest(method: String?, url: String?) = CioHttpRequest(httpClient, method, url)
}

class CioHttpRequest(
        private val httpClient: HttpClient,
        private val method: String?,
        private val url: String?)
    : LowLevelHttpRequest() {
    private val builder = HttpRequestBuilder()

    private var connectTimeout: Long? = null
    private var readTimeout: Long? = null
    private var writeTimeout: Long? = null

    init {
        builder.method = HttpMethod.parse(method ?: "GET")
        builder.url(url ?: "")
    }

    override fun addHeader(name: String?, value: String?) {
        builder.header(name ?: "", value ?: "")
    }

    override fun setTimeout(connectTimeout: Int, readTimeout: Int) {
        this.connectTimeout = connectTimeout.toLong()
        this.readTimeout = readTimeout.toLong()
    }

    override fun setWriteTimeout(writeTimeout: Int) {
        this.writeTimeout = writeTimeout.toLong()
    }

    override fun execute(): LowLevelHttpResponse {
        builder.timeout {
            connectTimeout?.let { connectTimeoutMillis = it }
            val operationTimeout = if (readTimeout != null && writeTimeout != null) {
                max(readTimeout!!, writeTimeout!!)
            } else if (readTimeout != null) {
                readTimeout
            } else if (writeTimeout != null) {
                writeTimeout
            } else {
                null
            }
            operationTimeout?.let {
                requestTimeoutMillis = it
                socketTimeoutMillis = it
            }
        }


        val response: HttpResponse = runBlocking { httpClient.request(builder) }
        return CioHttpResponse(response)
    }
}

class CioHttpResponse(private val response: HttpResponse) : LowLevelHttpResponse() {
    private val headers = mutableListOf<Pair<String, String>>()

    init {
        for (name in response.headers.names()) {
            headers += Pair(name, response.headers[name] ?: "")
        }
    }

    override fun getContent() = response.content.toInputStream()

    override fun getContentEncoding() = response.headers["Content-Encoding"]

    override fun getContentLength(): Long = response.contentLength() ?: 0

    override fun getContentType(): String? = response.contentType()?.contentType

    override fun getStatusLine() = response.status.description

    override fun getStatusCode() = response.status.value

    override fun getReasonPhrase() = response.status.description

    override fun getHeaderCount() = headers.size

    override fun getHeaderName(index: Int) = headers[index].first

    override fun getHeaderValue(index: Int) = headers[index].second
}