package vegancheckteam.plante_server.proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.util.filter
import io.ktor.util.toMap

private val FILTERED_OUT_HEADERS = setOf(
    "content-type", // controlled by Ktor and cannot be set explicitly
    "content-length", // controlled by Ktor and cannot be set explicitly
    "accept-encoding", // can be something we don't support (e.g. gzip)
    "authorization", // we REALLY don't want to leak our auth tokens
    "user-agent", // we'll set up our own user agent
    "useragent", // we'll set up our own user agent
    "host", // that's our server, but we're proxying the request to another server
)

fun proxyHeaders(call: ApplicationCall) = call.request.headers
    .filter { name, _ -> !FILTERED_OUT_HEADERS.contains(name.lowercase()) }

fun ensureCredentialsWereRemovedFromHeaders(
    call: ApplicationCall,
    result: HttpResponse
) {
    // Ensure we didn't send auth credentials to the target address.
    // This would make a nice test, but unit-tests in Ktor don't support
    // real requests, and the server doesn't have integrations tests at the moment.
    val credentials = call.request.headers["Authorization"] ?: call.request.headers["authorization"]
    if (credentials == null) {
        throw Exception("Seems the way auth works has changed - please edit this code to reflect the changes")
    }
    if (result.request.headers.toMap().containsKey(credentials)) {
        throw Exception("Credentials are not removed #1")
    }
    if (result.request.headers.toMap().values.flatten().contains(credentials)) {
        throw Exception("Credentials are not removed #2")
    }
}
