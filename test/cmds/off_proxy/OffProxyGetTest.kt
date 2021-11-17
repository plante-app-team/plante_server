package vegancheckteam.plante_server.cmds.off_proxy

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class OffProxyGetTest {
    @Test
    fun `a very fragile get proxy test`() {
        withPlanteTestApplication {
            val moderatorClientToken = registerModerator()
            val map = authedGet(moderatorClientToken, "/off_proxy_get/api/v0/product/3083680015394.json?fields=product_name").jsonMap()
            assertEquals("3083680015394", map["code"])
            val product = map["product"] as Map<*, *>
            assertTrue(product["name"].toString().isNotEmpty())
        }
    }

    @Test
    fun `normal user cannot use the get proxy`() {
        withPlanteTestApplication {
            val clientToken = register()
            val map = authedGet(clientToken, "/off_proxy_get/api/v0/product/3083680015394.json?fields=product_name").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
