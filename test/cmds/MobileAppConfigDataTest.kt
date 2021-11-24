package vegancheckteam.plante_server.cmds

import java.util.Base64
import kotlin.test.assertEquals
import org.junit.Test
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class MobileAppConfigDataTest {
    @Test
    fun `user data included`() {
        withPlanteTestApplication {
            val user = register()

            var map = authedGet(user, "/update_user_data/",
                queryParams = mapOf(
                    "name" to "Bob",
                    "gender" to "male",
                    "birthday" to "20.07.1993"),
                queryParamsLists = mapOf(
                    "langsPrioritized" to listOf("en", "ru")
                )).jsonMap()
            assertEquals(map["result"], "ok")

            map = authedGet(user, "/mobile_app_config/").jsonMap()
            val userJson = map["user_data"] as Map<*, *>
            assertEquals("Bob", userJson["name"])
            assertEquals("male", userJson["gender"])
            assertEquals("20.07.1993", userJson["birthday"])
            assertEquals(listOf("en", "ru"), userJson["langs_prioritized"])
        }
    }

    @Test
    fun `with nominatim enabled`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/mobile_app_config/", mapOf(
                "globalConfigOverride" to createGlobalConfigWithNominatimEnabled(true)
            )).jsonMap()
            assertEquals(true, map["nominatim_enabled"])
        }
    }

    @Test
    fun `with nominatim disabled`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/mobile_app_config/", mapOf(
                "globalConfigOverride" to createGlobalConfigWithNominatimEnabled(false)
            )).jsonMap()
            assertEquals(false, map["nominatim_enabled"])
        }
    }

    private fun createGlobalConfigWithNominatimEnabled(enabled: Boolean): String {
        val str = """
            {
              "psql_url":"",
              "psql_user":"",
              "psql_pass":"",
              "db_connection_attempts_timeout_seconds":0,
              "jwt_secret":"",
              "osm_prod_user":"",
              "osm_prod_password":"",
              "osm_testing_user":"",
              "osm_testing_password":"",
              "off_prod_user":"",
              "off_prod_password":"",
              "off_testing_user":"",
              "off_testing_password":"",
              "always_moderator_name":"",
              "allow_cors":false,
              "ios_backend_private_key_file_path":"",
              "metrics_endpoint":"a",
              "nominatim_enabled":$enabled
            }
        """.trimIndent()
        return Base64.getEncoder().encodeToString(str.toByteArray())
    }
}
