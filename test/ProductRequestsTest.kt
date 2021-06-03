package vegancheckteam.plante_server

import io.ktor.server.testing.withTestApplication
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.plante_server.db.MAX_PRODUCT_CHANGES_COUNT
import vegancheckteam.plante_server.db.ProductScanTable
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.cmds.PRODUCT_SCAN_STORAGE_DAYS_LIMIT
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.get
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductRequestsTest {
    @Test
    fun `create, get and update product`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative"
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])
        }
    }

    @Test
    fun `create product without veg statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(clientToken, "/create_update_product/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])
        }
    }

    @Test
    fun `all possible veg statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            for (status in VegStatus.values()) {
                map = authedGet(
                    clientToken, "/create_update_product/?"
                            + "barcode=${barcode}"
                            + "&vegetarianStatus=${status.statusName}"
                            + "&veganStatus=${status.statusName}"
                ).jsonMap()
                assertEquals("ok", map["result"])

                map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
                assertEquals(status.statusName, map["vegetarian_status"])
                assertEquals(status.statusName, map["vegan_status"])
            }
        }
    }

    @Test
    fun `invalid vegetarian statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            val map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=nope"
            ).jsonMap()
            assertEquals("invalid_veg_status", map["error"])
        }
    }

    @Test
    fun `invalid vegan statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            val map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=nope"
            ).jsonMap()
            assertEquals("invalid_veg_status", map["error"])
        }
    }

    @Test
    fun `when veg status is not provided then it is not updated`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=positive"
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("positive", map["vegan_status"])

            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=possible"
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("possible", map["vegetarian_status"])
            assertEquals("positive", map["vegan_status"])
        }
    }

    @Test
    fun `get product changes history`() {
        withTestApplication({ module(testing = true) }) {
            val timeStart = ZonedDateTime.now().toEpochSecond()
            Thread.sleep(1001) // To make changes times different

            var map = get("/register_user/?deviceId=123&googleIdToken=${UUID.randomUUID()}").jsonMap()
            val userClientToken = map["client_token"] as String
            val userId = map["user_id"] as String

            val barcode = UUID.randomUUID().toString()
            map = authedGet(
                userClientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            Thread.sleep(1001) // To make changes times different

            map = authedGet(
                userClientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative"
            ).jsonMap()
            assertEquals("ok", map["result"])

            Thread.sleep(1001) // To make changes times different

            val moderatorClientToken = registerModerator()

            map = authedGet(moderatorClientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            val changes = map["changes"] as List<*>
            assertEquals(2, changes.size, changes.toString())

            val change1 = changes[0] as Map<*, *>
            assertEquals(barcode, change1["barcode"])
            assertEquals(userId, change1["editor_id"])
            val time1 = change1["time"] as Int
            assertTrue(timeStart < time1 && time1 < ZonedDateTime.now().toEpochSecond())
            val updatedProduct1 = change1["updated_product"] as Map<*, *>
            assertEquals(barcode, updatedProduct1["barcode"])
            assertEquals("unknown", updatedProduct1["vegetarian_status"])
            assertEquals("unknown", updatedProduct1["vegan_status"])

            val change2 = changes[1] as Map<*, *>
            assertEquals(barcode, change2["barcode"])
            assertEquals(userId, change2["editor_id"])
            val time2 = change2["time"] as Int
            assertTrue(timeStart < time2 && time2 < ZonedDateTime.now().toEpochSecond())
            val updatedProduct2 = change2["updated_product"] as Map<*, *>
            assertEquals(barcode, updatedProduct2["barcode"])
            assertEquals("positive", updatedProduct2["vegetarian_status"])
            assertEquals("negative", updatedProduct2["vegan_status"])

            assertTrue(time1 < time2, "$time1 $time2")
        }
    }

    @Test
    fun `cannot get product changes history by normal user`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `duplicating product changes are not stored in product changes history`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            // Save it twice
            val barcode = UUID.randomUUID().toString()
            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            val changes = map["changes"] as List<*>
            assertEquals(1, changes.size, changes.toString())
        }
    }

    @Test
    fun `product changes history is limited`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            val barcode = UUID.randomUUID().toString()
            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            Thread.sleep(1001) // To make changes times different

            for (index in 0 until MAX_PRODUCT_CHANGES_COUNT) {
                map = authedGet(
                    clientToken, "/create_update_product/?"
                            + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative"
                ).jsonMap()
                assertEquals("ok", map["result"])
                map = authedGet(
                    clientToken, "/create_update_product/?"
                            + "barcode=${barcode}&vegetarianStatus=negative&veganStatus=positive"
                ).jsonMap()
                assertEquals("ok", map["result"])
            }

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            val changes = map["changes"] as List<*>
            assertEquals(MAX_PRODUCT_CHANGES_COUNT, changes.size, changes.toString())

            // "unknown" status was the first one and we expect this change to be erased
            // because changes limit was reached
            for (change in changes) {
                val changeMap = change as Map<*, *>
                val updatedProduct = changeMap["updated_product"] as Map<*, *>
                assertNotEquals("unknown", updatedProduct["vegetarian_status"])
                assertNotEquals("unknown", updatedProduct["vegan_status"])
            }
        }
    }

    @Test
    fun `product scan logic`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()

            // NOTE: the product is not registered
            val barcode = UUID.randomUUID().toString()

            var now = ZonedDateTime.now()

            var map = authedGet(clientToken, "/product_scan/?barcode=${barcode}&testingNow=${now.toEpochSecond()}").jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/product_scan/?barcode=${barcode}&testingNow=${now.toEpochSecond()}").jsonMap()
            assertEquals("ok", map["result"])

            // 2 added
            transaction {
                val count = ProductScanTable.select {
                    ProductScanTable.productBarcode eq barcode
                }.count()
                assertEquals(2, count)
            }

            now = now.plusDays(PRODUCT_SCAN_STORAGE_DAYS_LIMIT).plusSeconds(1)
            map = authedGet(clientToken, "/product_scan/?barcode=${barcode}&testingNow=${now.toEpochSecond()}").jsonMap()
            assertEquals("ok", map["result"])

            // 1 added 2 removed
            transaction {
                val count = ProductScanTable.select {
                    ProductScanTable.productBarcode eq barcode
                }.count()
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun `creation and update of a product doesn't mess with other products`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            // Create product 1
            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode1}&vegetarianStatus=unknown&veganStatus=unknown"
            ).jsonMap()
            assertEquals("ok", map["result"])

            // Create product 2
            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode2}&vegetarianStatus=positive&veganStatus=positive"
            ).jsonMap()
            assertEquals("ok", map["result"])
            // Update product 2
            map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode2}&vegetarianStatus=negative&veganStatus=negative"
            ).jsonMap()
            assertEquals("ok", map["result"])

            // Ensure product 1 has same data as during creation

            map = authedGet(clientToken, "/product_data/?barcode=${barcode1}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])
        }
    }
}
