package vegancheckteam.plante_server.proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.util.filter
import io.ktor.util.toMap

fun proxyHeaders(call: ApplicationCall) = call.request.headers
    // [Content-Type] is controlled by the engine and cannot be set explicitly
    .filter { name, _ -> name != "Content-Type" }
    // We don't want to expose our client token to the external world
    .filter { name, _ -> name != "Authorization" }

fun ensureCredentialsWereRemovedFromHeaders(
    call: ApplicationCall,
    result: HttpResponse
) {
    // Ensure we didn't send auth credentials to the target address.
    // This would make a nice test, but unit-tests in Ktor don't support
    // real requests, and the server doesn't have integrations tests at the moment.
    val credentials = call.request.headers["Authorization"]
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
