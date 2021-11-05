package vegancheckteam.plante_server.cmds.moderation

import java.util.UUID
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ModerationRequests_ProductModeration_Test {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `product veg statuses moderation`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("negative", map["vegan_status"])
            assertEquals("moderator", map["vegan_status_source"])
        }
    }

    @Test
    fun `product veg statuses moderation by simple user`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            map = authedGet(clientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "veganStatus=negative").jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    @Test
    fun `product veg statuses moderation with invalid veg statuses`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=unknown").jsonMap()
            assertEquals("ok", map["result"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/moderate_product_veg_statuses/?barcode=${barcode}&"
                    + "veganStatus=NENENEGATIVE").jsonMap()
            assertEquals("invalid_veg_status", map["error"])
        }
    }

    @Test
    fun `normal user cannot change veg status set by a moderator`() {
        withPlanteTestApplication {
            val user = register()
            val barcode = UUID.randomUUID().toString()

            // Create with an unknown vegan status
            var map = authedGet(user, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "veganStatus" to "unknown")).jsonMap()
            assertEquals("ok", map["result"])

            // Verify user's vegan status is used
            map = authedGet(user, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("unknown", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            // Now moderator moderates the product
            val moderator = registerModerator()
            map = authedGet(moderator, "/moderate_product_veg_statuses/", mapOf(
                "barcode" to barcode,
                "veganStatus" to "negative")).jsonMap()
            assertEquals("ok", map["result"])

            // And user tries to change to status to another value
            map = authedGet(user, "/create_update_product/?", mapOf(
                "barcode" to barcode,
                "veganStatus" to "positive")).jsonMap()
            assertEquals("ok", map["result"])

            // The final vegan status is the one set by the moderator
            map = authedGet(user, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals("negative", map["vegan_status"])
            assertEquals("moderator", map["vegan_status_source"])
        }
    }

    @Test
    fun `clear product veg statuses`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])

            val moderatorClientToken = registerModerator()
            map = authedGet(moderatorClientToken, "/clear_product_veg_statuses/?barcode=${barcode}").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["vegan_status"])
            assertEquals(null, map["vegan_status_source"])
        }
    }

    @Test
    fun `clear product veg statuses by simple user`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/?"
                        + "barcode=${barcode}&veganStatus=negative").jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/clear_product_veg_statuses/?barcode=${barcode}").jsonMap()
            assertEquals("denied", map["error"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals("negative", map["vegan_status"])
            assertEquals("community", map["vegan_status_source"])
        }
    }

    @Test
    fun `specify product moderator choice reason`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])

            // Specify reason without text
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "veganChoiceReason" to "2",
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(2, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])

            // Specify reason with text
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "veganChoiceReason" to "4",
                "veganSourcesText" to "General Kenobi!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(4, map["moderator_vegan_choice_reason"])
            assertEquals("General Kenobi!", map["moderator_vegan_sources_text"])

            // Clear reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
            )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/product_data/?barcode=${barcode}").jsonMap()
            assertEquals(barcode, map["barcode"])
            assertEquals(null, map["moderator_vegan_choice_reason"])
            assertEquals(null, map["moderator_vegan_sources_text"])
        }
    }

    @Test
    fun `new product moderator choice reason does not erase reasons of other products`() {
        withPlanteTestApplication {
            val clientToken = register()
            val moderatorClientToken = registerModerator()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode1,
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode2,
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])

            // Product 1 reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode1,
                "veganChoiceReason" to "2",
                "veganSourcesText" to "there!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Product 2 reason
            map = authedGet(moderatorClientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode2,
                "veganChoiceReason" to "4",
                "veganSourcesText" to "You're a bold one!",
            )).jsonMap()
            assertEquals("ok", map["result"])

            // Verify product 1 reason
            map = authedGet(clientToken, "/product_data/?barcode=${barcode1}").jsonMap()
            assertEquals(barcode1, map["barcode"])
            assertEquals(2, map["moderator_vegan_choice_reason"])
            assertEquals("there!", map["moderator_vegan_sources_text"])

            // Verify product 2 reason
            map = authedGet(clientToken, "/product_data/?barcode=${barcode2}").jsonMap()
            assertEquals(barcode2, map["barcode"])
            assertEquals(4, map["moderator_vegan_choice_reason"])
            assertEquals("You're a bold one!", map["moderator_vegan_sources_text"])
        }
    }

    @Test
    fun `specify product moderator choice reason by simple user`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "negative",
                )).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/specify_moderator_choice_reason/", mapOf(
                "barcode" to barcode,
                "veganChoiceReason" to "2",
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }
}
