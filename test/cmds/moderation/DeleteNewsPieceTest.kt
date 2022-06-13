package vegancheckteam.plante_server.cmds.moderation

import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import vegancheckteam.plante_server.test_utils.deleteNewsPieceCmd
import vegancheckteam.plante_server.test_utils.putProductToShopCmd
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.requestNewsCmd
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class DeleteNewsPieceTest {
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
    fun `delete news piece`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID(name = "Helen")
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(2, news.size, news.toString())

            val newsPiece1 = news[0].toMutableMap()
            val newsPiece2 = news[1].toMutableMap()
            val newsPiece1Id = newsPiece1["id"] as Int
            val newsPiece2Id = newsPiece2["id"] as Int
            newsPiece1["id"] = null
            newsPiece2["id"] = null

            val expected = setOf<Map<*, *>>(
                mapOf(
                    "id" to null,
                    "lat" to 1.0,
                    "lon" to 1.0,
                    "creator_user_id" to userId,
                    "creator_user_name" to "Helen",
                    "creation_time" to 123,
                    "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                    "data" to mapOf(
                        "barcode" to barcode1,
                        "shop_uid" to shop1.asStr,
                    )
                ),
                mapOf(
                    "id" to null,
                    "lat" to 1.0,
                    "lon" to 1.0,
                    "creator_user_id" to userId,
                    "creator_user_name" to "Helen",
                    "creation_time" to 124,
                    "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                    "data" to mapOf(
                        "barcode" to barcode2,
                        "shop_uid" to shop2.asStr,
                    )
                ),
            )
            assertTrue(expected.contains(newsPiece1), newsPiece1.toString())
            assertTrue(expected.contains(newsPiece2), newsPiece2.toString())

            // Not let's delete
            deleteNewsPieceCmd(newsPiece1Id, registerModerator())
            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())

            assertEquals(newsPiece2Id, news[0]["id"])
        }
    }

    @Test
    fun `delete news piece as a normal user`() {
        withPlanteTestApplication {
            val (userToken, _) = registerAndGetTokenWithID(name = "Helen")
            val barcode1 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)

            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())
            val newsPiece1 = news[0].toMutableMap()
            val newsPiece1Id = newsPiece1["id"] as Int

            // Not let's delete
            val deleteResult = deleteNewsPieceCmd(newsPiece1Id, userToken)
            assertEquals("denied", deleteResult["error"])

            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())
            assertEquals(newsPiece1Id, news[0]["id"])
        }
    }
}
