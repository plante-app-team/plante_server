package vegancheckteam.plante_server.cmds

import io.ktor.server.testing.TestApplicationEngine
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.news.NewsPieceType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.putProductToShopCmd
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.requestNewsCmd
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class NewsPieceDataTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
                NewsPieceProductAtShopTable.deleteAll()
                NewsPieceTable.deleteAll()
            }
        }
    }

    @Test
    fun `request news piece`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID(name = "Bob")

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(2, news.size, news.toString())

            val expected = listOf(
                mapOf(
                    "id" to news[0]["id"],
                    "lat" to 1.0,
                    "lon" to 1.0,
                    "creator_user_id" to userId,
                    "creator_user_name" to "Bob",
                    "creation_time" to 124,
                    "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                    "data" to mapOf(
                        "barcode" to barcode2,
                        "shop_uid" to shop.asStr,
                    )
                ),
                mapOf(
                    "id" to news[1]["id"],
                    "lat" to 1.0,
                    "lon" to 1.0,
                    "creator_user_id" to userId,
                    "creator_user_name" to "Bob",
                    "creation_time" to 123,
                    "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                    "data" to mapOf(
                        "barcode" to barcode1,
                        "shop_uid" to shop.asStr,
                    )
                ),
            )
            assertEquals(expected, news)

            val piece0 = requestNewsPieceCmd(news[0]["id"] as Int, userToken)
            val piece1 = requestNewsPieceCmd(news[1]["id"] as Int, userToken)

            assertEquals(expected[0], piece0)
            assertEquals(expected[1], piece1)
        }
    }

    @Test
    fun `a not-existing news piece does not exist`() {
        withPlanteTestApplication {
            val userToken = register()

            requestNewsPieceCmd(
                id = 123456789,
                clientToken = userToken,
                expectedError = "news_piece_not_found",
            )
        }
    }


    fun TestApplicationEngine.requestNewsPieceCmd(id: Int, clientToken: String, expectedError: String? = null): Map<*, *> {
        val map = authedGet(clientToken, "/news_piece_data/", mapOf(
            "newsPieceId" to id.toString(),
        )).jsonMap()

        if (expectedError != null) {
            assertEquals(expectedError, map["error"])
        } else {
            assertNull(map["error"])
        }

        return map
    }
}
