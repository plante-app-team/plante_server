package vegancheckteam.plante_server.cmds.off_proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receive
import io.ktor.request.receiveParameters
import io.ktor.util.toMap
import io.ktor.util.url
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.proxy.ensureCredentialsWereRemovedFromHeaders
import vegancheckteam.plante_server.proxy.proxyHeaders

const val OFF_PROXY_POST_FORM_PATH = "/off_proxy_form_post"

/**
 * A proxy for POST requests sent from the mobile app to Open Food Facts.
 * The proxy is needed because OFF accepts POSTs only from authorized users,
 * and we cannot store OFF credentials in the mobile app - it's a vulnerability.
 */
suspend fun offProxyPostForm(call: ApplicationCall, client: HttpClient, testing: Boolean): Pair<HttpStatusCode, Any> {
    val proxiedPiece = call.url().replace(Regex(".*://(.*?)$OFF_PROXY_POST_FORM_PATH"), "")
    val target = if (testing) {
        "https://world.openfoodfacts.net/$proxiedPiece"
    } else {
        "https://world.openfoodfacts.org/$proxiedPiece"
    }

    val params = call.receiveParameters().toMap().toMutableMap()
    if (testing) {
        params["user_id"] = listOf(Config.instance.offTestingUser)
        params["password"] = listOf(Config.instance.offTestingPassword)
    } else {
        params["user_id"] = listOf(Config.instance.offProdUser)
        params["password"] = listOf(Config.instance.offProdPassword)
    }

    val result = client.submitForm<HttpResponse>(
        url = target,
        formParameters = Parameters.build {
            for (param in params) {
                appendAll(param.key, param.value)
            }
        }) {
            headers.appendAll(proxyHeaders(call))
            for (header in additionalOffHeaders(testing)) {
                headers.append(header.key, header.value)
            }
    }

    ensureCredentialsWereRemovedFromHeaders(call, result)

    return Pair(result.status, result.readText())
}
