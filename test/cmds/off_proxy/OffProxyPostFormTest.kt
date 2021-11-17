package vegancheckteam.plante_server.cmds.off_proxy

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import kotlin.test.assertTrue
import org.junit.Test
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class OffProxyPostFormTest {
    /**
     * NOTE: if this test fails, it might mean the mobile app is no longer able to
     * send POST requests to Open Food Facts!
     */
    @Test
    fun `a very fragile real save product test`() {
        withPlanteTestApplication {
            val user = register()

            val response = handleRequest(HttpMethod.Post, "/off_proxy_form_post/cgi/product_jqm2.pl") {
                addHeader("Authorization", "Bearer $user")
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf(
                    "comment" to " Plante server integration test",
                    "user_id" to " invalid_user_id",
                    "password" to " invalid_password",
                    "code" to " 1111111111111",
                    "product_name" to " Coca Cola Light",
                    "brands" to " Coca Cola",
                    "countries" to " Deutschland",
                    "lang" to " de",
                    "serving_size" to " 100g",
                    "selected_images" to " {}",
                    "images" to " {}",
                    "ingredients_text" to " Wasser, Kohlensäure, e150d, Citronensäure",
                    "ingredients_analysis_tags" to " []",
                    "additives_tags" to " [en:e150d, en:e950]",
                    "nutriment_energy_unit" to " kcal",
                    "nutrition_data_per" to " serving").formUrlEncode())
            }.response.content!!
            assertTrue(response.contains("\"status\":1"), response)
        }
    }

    @Test
    fun `a very fragile real save product test, without barcode`() {
        withPlanteTestApplication {
            val user = register()
            val response = handleRequest(HttpMethod.Post, "/off_proxy_form_post/cgi/product_jqm2.pl") {
                addHeader("Authorization", "Bearer $user")
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf(
                    "comment" to "Plante server integration test",
                    "user_id" to "invalid_user_id",
                    "password" to "invalid_password",
                    "product_name" to "Coca Cola Light",
                    "brands" to "Coca Cola",
                    "countries" to "Deutschland",
                    "lang" to "de",
                    "serving_size" to "100g",
                    "selected_images" to "{}",
                    "images" to "{}",
                    "ingredients_text" to "Wasser, Kohlensäure, e150d, Citronensäure",
                    "ingredients_analysis_tags" to "[]",
                    "additives_tags" to "[en:e150d, en:e950]",
                    "nutriment_energy_unit" to "kcal",
                    "nutrition_data_per" to "serving").formUrlEncode())
            }.response.content!!
            assertTrue(response.contains("no code or invalid code"), response)
        }
    }
}
