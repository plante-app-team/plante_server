package vegancheckteam.plante_server.cmds

import io.ktor.server.testing.TestApplicationEngine
import java.util.*
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.db.UserContributionTable
import vegancheckteam.plante_server.model.UserContributionType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class UserContributionsTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
                UserContributionTable.deleteAll()
            }
        }
    }

    @Test
    fun `edited product contribution`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var contributions = userContributionsData(clientToken)
            assertEquals(emptyList(), contributions)

            val map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "123")
            ).jsonMap()
            assertEquals("ok", map["result"])

            contributions = userContributionsData(clientToken)
            assertEquals(1, contributions.size, contributions.toString())
            assertEquals(listOf(mapOf(
                "time_utc" to 123,
                "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                "barcode" to barcode,
            )), contributions)
        }
    }

    @Test
    fun `DID NOT edit product - NOT contribution`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            val map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "edited" to "false", // !!!!!! Did not edit the product!
                    "testingNow" to "123")
            ).jsonMap()
            assertEquals("ok", map["result"])

            val contributions = userContributionsData(clientToken)
            assertEquals(emptyList(), contributions)
        }
    }

    @Test
    fun `put product to a shop contribution`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var contributions = userContributionsData(clientToken)
            assertEquals(emptyList(), contributions)

            val map = authedGet(clientToken, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to "123")).jsonMap()
            assertEquals("ok", map["result"])

            contributions = userContributionsData(clientToken)
            assertEquals(1, contributions.size, contributions.toString())
            assertEquals(listOf(mapOf(
                "time_utc" to 123,
                "type" to UserContributionType.PRODUCT_ADDED_TO_SHOP.persistentCode.toInt(),
                "barcode" to barcode,
                "shop_uid" to shop.asStr,
            )), contributions)
        }
    }

    @Test
    fun `make product report contribution`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var contributions = userContributionsData(clientToken)
            assertEquals(emptyList(), contributions)

            val map = authedGet(clientToken, "/make_report/", mapOf(
                "barcode" to barcode,
                "text" to "someText",
                "testingNow" to "123",
            )).jsonMap()
            assertEquals("ok", map["result"])

            contributions = userContributionsData(clientToken)
            assertEquals(1, contributions.size, contributions.toString())
            assertEquals(listOf(mapOf(
                "time_utc" to 123,
                "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                "barcode" to barcode,
            )), contributions)
        }
    }

    @Test
    fun `created a shop contribution`() {
        withPlanteTestApplication {
            val clientToken = register()

            var contributions = userContributionsData(clientToken)
            assertEquals(emptyList(), contributions)

            val fakeOsmResponses = String(
                Base64.getEncoder().encode(
                    CreateShopTestingOsmResponses("123456", "654321", "").toString()
                        .toByteArray()))
            val map = authedGet(clientToken, "/create_shop/", mapOf(
                "testingNow" to "123",
                "lat" to "-24",
                "lon" to "44",
                "name" to "myshop",
                "type" to "general",
                "testingResponsesJsonBase64" to fakeOsmResponses)).jsonMap()
            assertEquals("654321", map["osm_id"])

            contributions = userContributionsData(clientToken)
            transaction {
                print(UserContributionTable.selectAll().count())
            }
            assertEquals(1, contributions.size, contributions.toString())
            assertEquals(listOf(mapOf(
                "time_utc" to 123,
                "type" to UserContributionType.SHOP_CREATED.persistentCode.toInt(),
                "shop_uid" to "1:654321",
            )), contributions)
        }
    }

    @Test
    fun `request different contributions type`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "1")
            ).jsonMap()
            assertEquals("ok", map["result"])

            map = authedGet(clientToken, "/make_report/", mapOf(
                "barcode" to barcode,
                "text" to "someText",
                "testingNow" to "2",
                )
            ).jsonMap()
            assertEquals("ok", map["result"])

            // Both types
            var contributions = userContributionsData(
                clientToken, contributionsTypes = listOf(
                    UserContributionType.PRODUCT_EDITED,
                    UserContributionType.PRODUCT_REPORTED))
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 2,
                    "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
                mapOf(
                    "time_utc" to 1,
                    "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
            ), contributions)

            // First type
            contributions = userContributionsData(
                clientToken, contributionsTypes = listOf(
                    UserContributionType.PRODUCT_EDITED))
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 1,
                    "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
            ), contributions)

            // Second type
            contributions = userContributionsData(
                clientToken, contributionsTypes = listOf(
                    UserContributionType.PRODUCT_REPORTED))
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 2,
                    "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
            ), contributions)
        }
    }

    @Test
    fun `returned contributions order`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            // 1
            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "1"
                )
            ).jsonMap()
            assertEquals("ok", map["result"])
            // 3
            map = authedGet(
                clientToken, "/make_report/", mapOf(
                    "barcode" to barcode,
                    "text" to "someText",
                    "testingNow" to "3",
                )
            ).jsonMap()
            assertEquals("ok", map["result"])
            // 2
            map = authedGet(clientToken, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to "2")
            ).jsonMap()
            assertEquals("ok", map["result"])

            val contributions = userContributionsData(clientToken)
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 3,
                    "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
                mapOf(
                    "time_utc" to 2,
                    "type" to UserContributionType.PRODUCT_ADDED_TO_SHOP.persistentCode.toInt(),
                    "barcode" to barcode,
                    "shop_uid" to shop.asStr,
                ),
                mapOf(
                    "time_utc" to 1,
                    "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
            ), contributions)
        }
    }

    @Test
    fun `returned contributions limit`() {
        withPlanteTestApplication {
            val clientToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var map = authedGet(
                clientToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "1"
                )
            ).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(clientToken, "/put_product_to_shop/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "testingNow" to "2")
            ).jsonMap()
            assertEquals("ok", map["result"])
            map = authedGet(
                clientToken, "/make_report/", mapOf(
                    "barcode" to barcode,
                    "text" to "someText",
                    "testingNow" to "3",
                )
            ).jsonMap()
            assertEquals("ok", map["result"])

            // 3
            var contributions = userContributionsData(clientToken)
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 3,
                    "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
                mapOf(
                    "time_utc" to 2,
                    "type" to UserContributionType.PRODUCT_ADDED_TO_SHOP.persistentCode.toInt(),
                    "barcode" to barcode,
                    "shop_uid" to shop.asStr,
                ),
                mapOf(
                    "time_utc" to 1,
                    "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
            ), contributions)

            // 2
            contributions = userContributionsData(clientToken, limit = 2)
            assertEquals(listOf(
                mapOf(
                    "time_utc" to 3,
                    "type" to UserContributionType.PRODUCT_REPORTED.persistentCode.toInt(),
                    "barcode" to barcode,
                ),
                mapOf(
                    "time_utc" to 2,
                    "type" to UserContributionType.PRODUCT_ADDED_TO_SHOP.persistentCode.toInt(),
                    "barcode" to barcode,
                    "shop_uid" to shop.asStr,
                ),
            ), contributions)
        }
    }

    @Test
    fun `request contributions of other user as a moderator`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID()
            val barcode = UUID.randomUUID().toString()

            val map = authedGet(
                userToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "123")
            ).jsonMap()
            assertEquals("ok", map["result"])

            val moderatorToken = registerModerator()

            // The moderator doesn't have contributions
            var contributions = userContributionsData(moderatorToken)
            assertEquals(emptyList(), contributions)

            // The moderator can request other user's contributions
            contributions = userContributionsData(moderatorToken, targetUserId = userId)
            assertEquals(1, contributions.size, contributions.toString())
            assertEquals(listOf(mapOf(
                "time_utc" to 123,
                "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                "barcode" to barcode,
            )), contributions)
        }
    }

    @Test
    fun `request contributions of other user as a normal user`() {
        withPlanteTestApplication {
            val (userToken, firstUserId) = registerAndGetTokenWithID()
            val barcode = UUID.randomUUID().toString()

            var map = authedGet(
                userToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "123")
            ).jsonMap()
            assertEquals("ok", map["result"])

            val otherUserToken = register()

            // The other user doesn't have contributions
            var contributions = userContributionsData(otherUserToken)
            assertEquals(emptyList(), contributions)

            // The other user CANNOT request other user's contributions
            contributions = userContributionsData(otherUserToken, targetUserId = firstUserId, expectedError = "denied")
            assertEquals(emptyList(), contributions)

            // Now the other user has a contribution!
            map = authedGet(
                otherUserToken, "/create_update_product/", mapOf(
                    "barcode" to barcode,
                    "veganStatus" to "unknown",
                    "edited" to "true",
                    "testingNow" to "100")
            ).jsonMap()
            assertEquals("ok", map["result"])

            // The other user's contributions check
            contributions = userContributionsData(otherUserToken)
            assertEquals(listOf(mapOf(
                "time_utc" to 100,
                "type" to UserContributionType.PRODUCT_EDITED.persistentCode.toInt(),
                "barcode" to barcode,
            )), contributions)

            // But the other user still CANNOT request other user's contributions
            contributions = userContributionsData(otherUserToken, targetUserId = firstUserId, expectedError = "denied")
            assertEquals(emptyList(), contributions)
        }
    }

    private fun TestApplicationEngine.userContributionsData(
        clientToken: String,
        contributionsTypes: List<UserContributionType> = UserContributionType.values().toList(),
        limit: Int = 100,
        targetUserId: String? = null,
        expectedError: String? = null,
    ): List<Map<*, *>> {
        val queryParams = mutableMapOf("limit" to "$limit")
        targetUserId?.let { queryParams["userId"] = it }

        val map = authedGet(clientToken, "/user_contributions_data/",
            queryParams = queryParams,
            queryParamsLists = mapOf("contributionsTypes" to contributionsTypes.map { it.persistentCode.toString() }))
            .jsonMap()

        return if (expectedError == null) {
            val result = map["result"] as List<*>
            result.map { it as Map<*, *> }
        } else {
            assertEquals(expectedError, map["error"], map.toString())
            emptyList()
        }
    }
}
