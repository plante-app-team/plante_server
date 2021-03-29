package vegancheckteam.untitled_vegan_app_server

import io.ktor.server.testing.withTestApplication
import java.time.ZonedDateTime
import java.util.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import vegancheckteam.untitled_vegan_app_server.model.VegStatus
import vegancheckteam.untitled_vegan_app_server.test_utils.authedGet
import vegancheckteam.untitled_vegan_app_server.test_utils.get
import vegancheckteam.untitled_vegan_app_server.test_utils.jsonMap
import vegancheckteam.untitled_vegan_app_server.test_utils.register
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductRequestsTest {
    @Test
    fun `create, get and update product`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("product_not_found", map["error"])

            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegetarian_status"])
            assertEquals("community", map["vegetarian_status_source"])
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative").jsonMap()
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
                map = authedGet(clientToken, "/create_update_product/?"
                        + "barcode=${barcode}"
                        + "&vegetarianStatus=${status.statusName}"
                        + "&veganStatus=${status.statusName}").jsonMap()
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

            val map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=nope").jsonMap()
            assertEquals("invalid_veg_status", map["error"])
        }
    }

    @Test
    fun `invalid vegan statuses`() {
        withTestApplication({ module(testing = true) }) {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            val map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&veganStatus=nope").jsonMap()
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

            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=positive").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("positive", map["vegetarian_status"])
            assertEquals("positive", map["vegan_status"])

            map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=possible").jsonMap()
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
            map = authedGet(userClientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            Thread.sleep(1001) // To make changes times different

            map = authedGet(userClientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=positive&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            Thread.sleep(1001) // To make changes times different

            val moderator = User(
                id = UUID.randomUUID(),
                loginGeneration = 1,
                googleId = null)
            transaction {
                UserTable.insert {
                    it[id] = moderator.id
                    it[loginGeneration] = moderator.loginGeneration
                    it[name] = moderator.name
                    it[googleId] = moderator.googleId
                    it[userRightsGroup] = UserRightsGroup.MODERATOR.groupName
                }
            }
            val moderatorClientToken = JwtController.makeToken(moderator, "device id")

            map = authedGet(moderatorClientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            val changes = map["changes"] as List<*>
            assertEquals(2, changes.size, changes.toString())

            val change1 = changes[0] as Map<*,*>
            assertEquals(barcode, change1["barcode"])
            assertEquals(userId, change1["editor_id"])
            val time1 = change1["time"] as Int
            assertTrue(timeStart < time1 && time1 < ZonedDateTime.now().toEpochSecond())
            val updatedProduct1 = change1["updated_product"] as Map<*,*>
            assertEquals(barcode, updatedProduct1["barcode"])
            assertEquals("unknown", updatedProduct1["vegetarian_status"])
            assertEquals("unknown", updatedProduct1["vegan_status"])

            val change2 = changes[1] as Map<*,*>
            assertEquals(barcode, change2["barcode"])
            assertEquals(userId, change2["editor_id"])
            val time2 = change2["time"] as Int
            assertTrue(timeStart < time2 && time2 < ZonedDateTime.now().toEpochSecond())
            val updatedProduct2 = change2["updated_product"] as Map<*,*>
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
            var map = authedGet(clientToken, "/create_update_product/?"
                    + "barcode=${barcode}&vegetarianStatus=unknown&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_changes_data/?barcode=${barcode}").jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
