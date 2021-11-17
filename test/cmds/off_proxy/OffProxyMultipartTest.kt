package vegancheckteam.plante_server.cmds.off_proxy

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.utils.io.streams.asInput
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class OffProxyMultipartTest {
    /**
     * NOTE: if this test fails, it might mean the mobile app is no longer able to
     * send images to Open Food Facts!
     */
    @Test
    fun `a very fragile real product_image_upload test`() {
        withPlanteTestApplication {
            val user = register()

            val img = File("./assets_for_tests/front_coca_light_de.jpg")
            assertTrue(img.exists())

            val resp = handleRequest(HttpMethod.Post, "/off_proxy_multipart/cgi/product_image_upload.pl") {
                val boundary = "CoolBoundary"
                val fileBytes = img.readBytes()

                addHeader("Authorization", "Bearer $user")
                addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString())

                val createFormItem = { key: String, value: String ->
                    PartData.FormItem(value, { }, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline
                            .withParameter(ContentDisposition.Parameters.Name, key)
                            .toString()
                    ))
                }
                setBody(boundary, listOf(
                    createFormItem("user_id", "invalid_user_id"),
                    createFormItem("user_id", "invalid_user_id"),
                    createFormItem("comment", "Plante server integration test"),
                    createFormItem("lc", "de"),
                    createFormItem("code", "1111111111111"),
                    createFormItem("imagefield", "front_de"),
                    PartData.FileItem({ fileBytes.inputStream().asInput() }, {}, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.File
                            .withParameter(ContentDisposition.Parameters.Name, "imgupload_front_de")
                            .withParameter(ContentDisposition.Parameters.FileName, "front_coca_light_de.jpg")
                            .toString()
                    ))
                ))
            }

            assertEquals(resp.response.status(), HttpStatusCode.OK)
            val content = resp.response.content!!
            assertTrue(content.contains("\"status\":\"status ok\"")
                    || content.contains("we have already received an image with this file size"))
        }
    }

    @Test
    fun `a very fragile real product_image_upload test, without a barcode`() {
        withPlanteTestApplication {
            val user = register()

            val img = File("./assets_for_tests/front_coca_light_de.jpg")
            assertTrue(img.exists())

            val resp = handleRequest(HttpMethod.Post, "/off_proxy_multipart/cgi/product_image_upload.pl"){
                val boundary = "WebAppBoundary"
                val fileBytes = img.readBytes()

                addHeader("Authorization", "Bearer $user")
                addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString())

                val createFormItem = { key: String, value: String ->
                    PartData.FormItem(value, { }, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Inline
                            .withParameter(ContentDisposition.Parameters.Name, key)
                            .toString()
                    ))
                }
                setBody(boundary, listOf(
                    createFormItem("user_id", "invalid_user_id"),
                    createFormItem("user_id", "invalid_user_id"),
                    createFormItem("comment", "Plante server integration test"),
                    createFormItem("lc", "en"),
                    createFormItem("imagefield", "front_en"),
                    PartData.FileItem({ fileBytes.inputStream().asInput() }, {}, headersOf(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.File
                            .withParameter(ContentDisposition.Parameters.Name, "imgupload_front_en")
                            .withParameter(ContentDisposition.Parameters.FileName, "front_coca_light_de.jpg")
                            .toString()
                    ))
                ))
            }

            assertEquals(resp.response.status(), HttpStatusCode.OK)
            val content = resp.response.content!!
            assertTrue(content.contains("\"error\":\"No barcode specified or found in the image or filename.\""))
        }
    }
}
