package vegancheckteam.plante_server.cmds.off_proxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import kotlin.io.path.Path
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.proxy.MultipartProxy
import vegancheckteam.plante_server.multipart_proxy.MultipartProxyStorage

const val OFF_PROXY_MULTIPART_PATH = "/off_proxy_multipart"

/**
 * A proxy for files sent from the mobile app to Open Food Facts.
 * The proxy is needed because OFF accepts files only from authorized users,
 * and we cannot store OFF credentials in the mobile app - it's a vulnerability.
 */
suspend fun offProxyMultipart(
        call: ApplicationCall,
        client: HttpClient,
        testing: Boolean): Pair<HttpStatusCode, Any> {
    val resp = OffProxyImpl.proxy(call, client, testing)
    return Pair(resp.status, resp.readText())
}

private val storage = MultipartProxyStorage(
    maxSizeBytes = 1024 * 1024 * 100, // 100 megabytes
    maxLifetimeMillis = 1000 * 60, // 1 minute
    directory = Path("/tmp/off_proxy_multipart_files")
)

private object OffProxyImpl : MultipartProxy(storage) {
    override fun convertUrl(url: String, testing: Boolean): String {
        val proxiedPiece = url.replace(Regex(".*://(.*?)$OFF_PROXY_MULTIPART_PATH"), "")
        return if (testing) {
            "https://world.openfoodfacts.net/$proxiedPiece"
        } else {
            "https://world.openfoodfacts.org/$proxiedPiece"
        }
    }

    override fun additionalHeaders(testing: Boolean) = additionalOffHeaders(testing)

    override fun additionalFormData(testing: Boolean): List<PartData> = formData {
        if (testing) {
            append("user_id", Config.instance.offTestingUser)
            append("password", Config.instance.offTestingPassword)
        } else {
            append("user_id", Config.instance.offProdUser)
            append("password", Config.instance.offProdPassword)
        }
    }
}
