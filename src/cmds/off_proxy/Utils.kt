package vegancheckteam.plante_server.cmds.off_proxy

import java.util.Base64

fun additionalOffHeaders(testing: Boolean): Map<String, String> {
    val headers = mutableMapOf(
        "User-Agent" to "Plante app server (plante.application@gmail.com) - proxying mobile client request",
    )
    if (testing) {
        // That's how OFF's testing server works ¯\_(ツ)_/¯
        val credentials = String(Base64.getEncoder().encode("off:off".toByteArray()))
        val token = "Basic $credentials"
        headers["authorization"] = token
    }
    return headers
}
