package vegancheckteam.plante_server.cmds.off_proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.util.url
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.proxy.ensureCredentialsWereRemovedFromHeaders
import vegancheckteam.plante_server.proxy.proxyHeaders

const val OFF_PROXY_GET_PATH = "/off_proxy_get"

/**
 * OFF Dart SDK doesn't support Flutter Web platform, but we really want to
 * use it on the Web on our Plante Web Admin page.
 * Since the SDK doesn't work because of CORS, we can easily work around this
 * by proxying Plante Web Admin requests to OFF through server, which this
 * function does.
 */
suspend fun offProxyGet(call: ApplicationCall, user: User, client: HttpClient, testing: Boolean): Pair<HttpStatusCode, Any> {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        // Get requests to OFF don't require any authentication - the mobile
        // app can (and should) send them directly to OFF.
        return Pair(HttpStatusCode.OK, GenericResponse.failure("denied"))
    }
    val proxiedPiece = call.url().replace(Regex(".*://(.*?)$OFF_PROXY_GET_PATH/"), "")

    val target = "https://world.openfoodfacts.org/$proxiedPiece"
    val result = client.get<HttpResponse>(target) {
        headers.appendAll(proxyHeaders(call))
        for (header in additionalOffHeaders(testing)) {
            headers.append(header.key, header.value)
        }
        Log.i("offProxyGet", "target: $target, headers: ${headers.entries()}")
    }

    ensureCredentialsWereRemovedFromHeaders(call, result)

    return Pair(result.status, result.readText())
}
