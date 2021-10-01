package vegancheckteam.plante_server.cmds.moderation

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.util.url
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

const val OFF_PROXY_PATH = "/off_proxy_get"

suspend fun offProxyGet(call: ApplicationCall, user: User, client: HttpClient): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    val proxiedPiece = call.url().replace(Regex(".*://(.*?)$OFF_PROXY_PATH"), "")
    val result = client.get<HttpResponse>("https://world.openfoodfacts.org/$proxiedPiece")
    return result.readText()
}
