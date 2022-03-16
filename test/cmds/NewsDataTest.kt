package vegancheckteam.plante_server.cmds

import io.ktor.server.testing.TestApplicationEngine
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.base.kmToGrad
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.news.NewsPieceType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class NewsDataTest {
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
    fun `a news piece created when a product added to a shop`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertTrue(news.isEmpty(), news.toString())

            putProductToShop(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 123)

            news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            assertNotNull(newsPiece["id"])

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creation_time" to 123,
                "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                "data" to mapOf(
                    "barcode" to barcode,
                    "shop_uid" to shop.asStr,
                )
            )
            newsPiece["id"] = null
            assertEquals<Map<*, *>>(expected, newsPiece)
        }
    }

    @Test
    fun `products addition to multiple shops creates multiple news pieces`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShop(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShop(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(2, news.size, news.toString())

            val newsPiece1 = news[0].toMutableMap()
            val newsPiece2 = news[1].toMutableMap()
            newsPiece1["id"] = null
            newsPiece2["id"] = null

            val expected = setOf<Map<*, *>>(
                mapOf(
                    "id" to null,
                    "lat" to 1.0,
                    "lon" to 1.0,
                    "creator_user_id" to userId,
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
        }
    }

    @Test
    fun `second put_product_to_shop command for same product and shop does not create a second news piece`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShop(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 123)
            putProductToShop(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            assertNotNull(newsPiece["id"])

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creation_time" to 123,
                "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                "data" to mapOf(
                    "barcode" to barcode,
                    "shop_uid" to shop.asStr,
                )
            )
            newsPiece["id"] = null
            assertEquals<Map<*, *>>(expected, newsPiece)
        }
    }

    @Test
    fun `news order`() {
        val barcode1 = UUID.randomUUID().toString()
        val barcode2 = UUID.randomUUID().toString()
        val shop1 = generateFakeOsmUID()
        val shop2 = generateFakeOsmUID()

        withPlanteTestApplication {
            val userToken = register()

            putProductToShop(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShop(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            val data1 = news[0]["data"] as Map<*, *>
            val data2 = news[1]["data"] as Map<*, *>

            assertEquals(barcode2, data1["barcode"])
            assertEquals(shop2.asStr, data1["shop_uid"])
            assertEquals(barcode1, data2["barcode"])
            assertEquals(shop1.asStr, data2["shop_uid"])
        }

        // let's clean everything and do it again,
        // but with different [now]
        setUp()

        withPlanteTestApplication {
            val userToken = register()

            putProductToShop(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 124)
            putProductToShop(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 123)

            val news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            val data1 = news[0]["data"] as Map<*, *>
            val data2 = news[1]["data"] as Map<*, *>

            assertEquals(barcode1, data1["barcode"])
            assertEquals(shop1.asStr, data1["shop_uid"])
            assertEquals(barcode2, data2["barcode"])
            assertEquals(shop2.asStr, data2["shop_uid"])
        }
    }

    @Test
    fun `can regulate news request bounds size`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShop(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShop(userToken, barcode2, shop2, lat = 1.01, lon = 1.01, now = 123)

            var news = requestNews(userToken, 1.01, 0.9, 0.9, 1.01, now = 123)
            assertEquals(2, news.size, news.toString())

            news = requestNews(userToken, 1.0, 0.9, 0.9, 1.0, now = 123)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            newsPiece["id"] = null

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creation_time" to 123,
                "type" to NewsPieceType.PRODUCT_AT_SHOP.persistentCode.toInt(),
                "data" to mapOf(
                    "barcode" to barcode1,
                    "shop_uid" to shop1.asStr,
                )
            )
            assertEquals<Map<*, *>>(expected, newsPiece)
        }
    }

    @Test
    fun `too big news bounds size`() {
        withPlanteTestApplication {
            val userToken = register()

            val newsMaxSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS)
            requestNews(
                userToken,
                north = newsMaxSize + 0.000000001,
                south = 0.0,
                west = 0.0,
                east = newsMaxSize + 0.000000001,
                expectedError = "area_too_big")
            requestNews(
                userToken,
                north = newsMaxSize - 0.001,
                south = 0.0,
                west = 0.0,
                east = newsMaxSize - 0.001,
                expectedError = null)
        }
    }

    private fun tooBigAreaTest(
        northEastBounds: Pair<Double, Double>,
        southWestBounds: Pair<Double, Double>) {
        withPlanteTestApplication {
            val clientToken = register()
            requestNews(
                clientToken,
                north = northEastBounds.first,
                south = southWestBounds.first,
                west = southWestBounds.second,
                east = northEastBounds.second,
                expectedError = "area_too_big")
        }
    }

    @Test
    fun `normal too big area`() {
        tooBigAreaTest(
            northEastBounds = Pair(10.0, 10.0),
            southWestBounds = Pair(0.0, 0.0),
        )
    }

    @Test
    fun `england too big area`() {
        tooBigAreaTest(
            northEastBounds = Pair(5.0, 5.0),
            southWestBounds = Pair(-5.0, -5.0),
        )
    }

    @Test
    fun `fiji too big area`() {
        tooBigAreaTest(
            northEastBounds = Pair(11.0, -175.0),
            southWestBounds = Pair(9.0, 175.0),
        )
    }

    @Test
    fun `outdated news are deleted`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            var now = 0L
            putProductToShop(
                userToken,
                barcode1,
                shop1,
                lat = 1.0,
                lon = 1.0,
                now = now)
            var news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
            assertEquals(1, news.size, news.toString())

            val barcode2 = UUID.randomUUID().toString()
            val shop2 = generateFakeOsmUID()
            now = TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS / 2)
            putProductToShop(
                userToken,
                barcode2,
                shop2,
                lat = 1.0,
                lon = 1.0,
                now = now)
            news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
            assertEquals(2, news.size, news.toString())

            val barcode3 = UUID.randomUUID().toString()
            val shop3 = generateFakeOsmUID()
            now = TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS) + 1
            putProductToShop(
                userToken,
                barcode3,
                shop3,
                lat = 1.0,
                lon = 1.0,
                now = now)
            news = requestNews(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
            // Still 2 news
            assertEquals(2, news.size, news.toString())

            // Let's ensure the first news piece is removed, the last 2 remain
            val data1 = news[0]["data"] as Map<*, *>
            val data2 = news[1]["data"] as Map<*, *>

            assertEquals(data1["barcode"], barcode3)
            assertEquals(data1["shop_uid"], shop3.asStr)
            assertEquals(data2["barcode"], barcode2)
            assertEquals(data2["shop_uid"], shop2.asStr)
        }
    }

    private fun areaTest(
        center: Pair<Double, Double>,
        northEastBounds: Pair<Double, Double>,
        southWestBounds: Pair<Double, Double>) {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            val osmUids = listOf(
                generateFakeOsmUID(),
                generateFakeOsmUID(),
                generateFakeOsmUID(),
                generateFakeOsmUID(),
                generateFakeOsmUID(),
            )
            val boundsMaxSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS)
            val osmUidsWithCoords = mapOf(
                osmUids[0] to Pair(center.first, center.second), // Center
                osmUids[1] to Pair(southWestBounds.first - boundsMaxSize, center.second), // Too much west
                osmUids[2] to Pair(northEastBounds.first + boundsMaxSize, center.second), // Too much east
                osmUids[3] to Pair(center.first, southWestBounds.second - boundsMaxSize), // Too much south
                osmUids[4] to Pair(center.first, northEastBounds.second + boundsMaxSize), // Too much north
            )

            val clientToken = register()
            for (entry in osmUidsWithCoords.entries) {
                val osmUid = entry.key
                val lat = entry.value.first
                val lon = entry.value.second
                putProductToShop(clientToken, barcode, osmUid, lat, lon)
            }
            val news = requestNews(
                clientToken,
                north = northEastBounds.first,
                south = southWestBounds.first,
                west = southWestBounds.second,
                east = northEastBounds.second)
            val newsData = news.map { it["data"] as Map<*, *> }
            assertTrue(newsData.any { it["shop_uid"] == osmUids[0].asStr })
            assertFalse(newsData.any { it["shop_uid"] == osmUids[1].asStr })
            assertFalse(newsData.any { it["shop_uid"] == osmUids[2].asStr })
            assertFalse(newsData.any { it["shop_uid"] == osmUids[3].asStr })
            assertFalse(newsData.any { it["shop_uid"] == osmUids[4].asStr })
        }
    }

    @Test
    fun `normal area`() {
        val boundsHalfSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS) / 4
        areaTest(
            center = Pair(1.5, 1.5),
            northEastBounds = Pair(1.5 + boundsHalfSize, 1.5 + boundsHalfSize),
            southWestBounds = Pair(1.5 - boundsHalfSize, 1.5 - boundsHalfSize),
        )
    }

    @Test
    fun `england area`() {
        val boundsHalfSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS) / 4
        areaTest(
            center = Pair(0.0, 0.0),
            northEastBounds = Pair(0.0 + boundsHalfSize, 0.0 + boundsHalfSize),
            southWestBounds = Pair(0.0 - boundsHalfSize, 0.0 - boundsHalfSize),
        )
    }

    @Test
    fun `fiji area`() {
        val boundsHalfSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS) / 4
        areaTest(
            center = Pair(10.0, 180.0),
            northEastBounds = Pair(10.0 + boundsHalfSize, -180.0 + boundsHalfSize),
            southWestBounds = Pair(10.0 - boundsHalfSize, 180.0 - boundsHalfSize),
        )
    }

    private fun TestApplicationEngine.requestNews(
        clientToken: String,
        north: Double,
        south: Double,
        west: Double,
        east: Double,
        now: Long? = null,
        expectedError: String? = null,
    ): List<Map<*, *>> {
        val params = mutableMapOf(
            "north" to north.toString(),
            "south" to south.toString(),
            "east" to east.toString(),
            "west" to west.toString(),
        )
        if (now != null) {
            params["testingNow"] = now.toString()
        }
        val map = authedGet(clientToken, "/news_data/", params).jsonMap()

        return if (expectedError == null) {
            assertNull(map["error"], map.toString())
            val result = map["results"] as List<*>
            result.map { it as Map<*, *> }
        } else {
            assertEquals(expectedError, map["error"], map.toString())
            emptyList()
        }
    }

    private fun TestApplicationEngine.putProductToShop(
        clientToken: String,
        barcode: String,
        shop: OsmUID,
        lat: Double,
        lon: Double,
        now: Long? = null,
    ) {
        val params = mutableMapOf(
            "barcode" to barcode,
            "shopOsmUID" to shop.asStr,
            "lat" to lat.toString(),
            "lon" to lon.toString(),
        )
        if (now != null) {
            params["testingNow"] = now.toString()
        }
        val map = authedGet(clientToken, "/put_product_to_shop/", params).jsonMap()
        assertEquals("ok", map["result"])
    }
}
