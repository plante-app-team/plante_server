package vegancheckteam.plante_server.cmds

import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
import vegancheckteam.plante_server.model.news.NewsPieceType
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.banUserCmd
import vegancheckteam.plante_server.test_utils.createShopCmd
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.moveProductsDeleteShopCmd
import vegancheckteam.plante_server.test_utils.putProductToShopCmd
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.requestNewsCmd
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
            val (userToken, userId) = registerAndGetTokenWithID(name = "Bob")

            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertTrue(news.isEmpty(), news.toString())

            putProductToShopCmd(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 123)

            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            assertNotNull(newsPiece["id"])

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creator_user_name" to "Bob",
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
            val (userToken, userId) = registerAndGetTokenWithID(name = "Helen")
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
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
        }
    }

    @Test
    fun `second put_product_to_shop command for same product and shop does not create a second news piece`() {
        withPlanteTestApplication {
            val (userToken, userId) = registerAndGetTokenWithID(name = "Jake")
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            assertNotNull(newsPiece["id"])

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creator_user_name" to "Jake",
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

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            val news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
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

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 124)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 1.0, lon = 1.0, now = 123)

            val news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 124)
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
            val (userToken, userId) = registerAndGetTokenWithID(name = "Donald")
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 1.01, lon = 1.01, now = 123)

            var news = requestNewsCmd(userToken, 1.01, 0.9, 0.9, 1.01, now = 123)
            assertEquals(2, news.size, news.toString())

            news = requestNewsCmd(userToken, 1.0, 0.9, 0.9, 1.0, now = 123)
            assertEquals(1, news.size, news.toString())

            val newsPiece = news[0].toMutableMap()
            newsPiece["id"] = null

            val expected = mapOf(
                "id" to null,
                "lat" to 1.0,
                "lon" to 1.0,
                "creator_user_id" to userId,
                "creator_user_name" to "Donald",
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
            requestNewsCmd(
                userToken,
                north = newsMaxSize + 0.000000001,
                south = 0.0,
                west = 0.0,
                east = newsMaxSize + 0.000000001,
                expectedError = "area_too_big")
            requestNewsCmd(
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
            requestNewsCmd(
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
            putProductToShopCmd(
                userToken,
                barcode1,
                shop1,
                lat = 1.0,
                lon = 1.0,
                now = now)
            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
            assertEquals(1, news.size, news.toString())

            val barcode2 = UUID.randomUUID().toString()
            val shop2 = generateFakeOsmUID()
            now = TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS / 2)
            putProductToShopCmd(
                userToken,
                barcode2,
                shop2,
                lat = 1.0,
                lon = 1.0,
                now = now)
            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
            assertEquals(2, news.size, news.toString())

            val barcode3 = UUID.randomUUID().toString()
            val shop3 = generateFakeOsmUID()
            now = TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS) + 1
            putProductToShopCmd(
                userToken,
                barcode3,
                shop3,
                lat = 1.0,
                lon = 1.0,
                now = now)
            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now)
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
                putProductToShopCmd(clientToken, barcode, osmUid, lat, lon)
            }
            val news = requestNewsCmd(
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

    @Test
    fun `news pagination`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcodes = mutableListOf<String>()
            for (i in 1..(NEWS_PAGE_SIZE*2.5).toInt()) {
                barcodes.add(UUID.randomUUID().toString())
            }
            val shop = generateFakeOsmUID()

            var now = 1L
            for (barcode in barcodes) {
                putProductToShopCmd(userToken, barcode, shop, lat = 1.0, lon = 1.0, now = ++now)
            }

            // Page 0
            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now, page = 0, expectedLastPage = false)
            assertEquals(NEWS_PAGE_SIZE, news.size, news.toString())
            var newsData = news.map { it["data"] as Map<*, *> }
            for (barcode in barcodes.reversed().subList(0, NEWS_PAGE_SIZE)) {
                assertTrue(newsData.any { it["barcode"] == barcode }, "expected barcode: $barcode, all: $newsData")
            }

            // Page 1
            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now, page = 1, expectedLastPage = false)
            assertEquals(NEWS_PAGE_SIZE, news.size, news.toString())
            newsData = news.map { it["data"] as Map<*, *> }
            for (barcode in barcodes.reversed().subList(NEWS_PAGE_SIZE, NEWS_PAGE_SIZE * 2)) {
                assertTrue(newsData.any { it["barcode"] == barcode }, "expected barcode: $barcode, all: $newsData")
            }

            // Page 2
            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = now, page = 2, expectedLastPage = true)
            assertEquals(NEWS_PAGE_SIZE / 2, news.size, news.toString())
            newsData = news.map { it["data"] as Map<*, *> }
            for (barcode in barcodes.reversed().subList(NEWS_PAGE_SIZE * 2, barcodes.size)) {
                assertTrue(newsData.any { it["barcode"] == barcode }, "expected barcode: $barcode, all: $newsData")
            }
        }
    }

    @Test
    fun `news piece about a product is deleted when the product is voted out`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcode = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode, shop, lat = 1.0, lon = 1.0)

            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1)
            assertEquals(1, news.size, news.toString())

            val map = authedGet(userToken, "/product_presence_vote/", mapOf(
                "barcode" to barcode,
                "shopOsmUID" to shop.asStr,
                "voteVal" to "0")).jsonMap()
            assertEquals("ok", map["result"])

            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1)
            assertEquals(0, news.size, news.toString())
        }
    }

    @Test
    fun `news pieces behaviour when a shop is deleted`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken, barcode2, shop, lat = 1.0, lon = 1.0, now = 123)

            var news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertEquals(2, news.size, news.toString())

            val moderator = registerModerator()
            val map = authedGet(moderator, "/delete_shop_locally/", mapOf(
                "shopOsmUID" to shop.asStr)).jsonMap()
            assertEquals("ok", map["result"])

            news = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 123)
            assertEquals(0, news.size, news.toString())
        }
    }

    @Test
    fun `news pieces behaviour when a shop is deleted with products move`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            createShopCmd(userToken, shop1.osmId, lat = 10.0, lon = 10.0)
            putProductToShopCmd(userToken, barcode1, shop1, lat = 10.0, lon = 10.0)
            createShopCmd(userToken, shop2.osmId, lat = 20.0, lon = 20.0)
            putProductToShopCmd(userToken, barcode2, shop2, lat = 20.0, lon = 20.0)

            // 1 news piece for each of the shops, yet
            var news = requestNewsCmd(userToken, 10.1, 9.9, 9.9, 10.1)
            assertEquals(1, news.size, news.toString())
            var newsData = news.map { it["data"] as Map<*, *> }
            assertEquals(1, newsData.count { it["shop_uid"] == shop1.asStr })

            news = requestNewsCmd(userToken, 20.1, 19.9, 19.9, 20.1)
            assertEquals(1, news.size, news.toString())
            newsData = news.map { it["data"] as Map<*, *> }
            assertEquals(1, newsData.count { it["shop_uid"] == shop2.asStr })

            val moderator = registerModerator()
            moveProductsDeleteShopCmd(
                moderator,
                badShop = shop1,
                goodShop = shop2,
                goodShopLat = 20.0,
                goodShopLon = 20.0,
            )

            // 0 news piece for the deleted shop, 2 news pieces for the remained shop
            news = requestNewsCmd(userToken, 10.1, 9.9, 9.9, 10.1)
            assertEquals(0, news.size, news.toString())
            newsData = news.map { it["data"] as Map<*, *> }
            assertEquals(0, newsData.count { it["shop_uid"] == shop1.asStr })
            assertEquals(0, newsData.count { it["shop_uid"] == shop2.asStr })

            news = requestNewsCmd(userToken, 20.1, 19.9, 19.9, 20.1)
            assertEquals(2, news.size, news.toString())
            newsData = news.map { it["data"] as Map<*, *> }
            assertEquals(0, newsData.count { it["shop_uid"] == shop1.asStr })
            assertEquals(2, newsData.count { it["shop_uid"] == shop2.asStr })
        }
    }

    @Test
    fun `latestSecsUtc param`() {
        withPlanteTestApplication {
            val userToken = register()
            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val barcode3 = UUID.randomUUID().toString()
            val shop = generateFakeOsmUID()

            putProductToShopCmd(userToken, barcode1, shop, lat = 1.0, lon = 1.0, now = 100)
            putProductToShopCmd(userToken, barcode2, shop, lat = 1.0, lon = 1.0, now = 101)
            putProductToShopCmd(userToken, barcode3, shop, lat = 1.0, lon = 1.0, now = 102)

            val newsData = { news: List<Map<*, *>> ->
                news.map { it["data"] as Map<*, *> }
            }
            // News request without the 'until' params is expected to return all
            // news up until Now
            val newsUntilNow = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 102)
            val dataUntilNow = newsData(newsUntilNow)
            val barcodesUntilNow = dataUntilNow.map { it["barcode"] }
            assertEquals(listOf(barcode3, barcode2, barcode1), barcodesUntilNow)

            // If the provided 'until' param equal to Now, the returned data is expected to be same
            val newsUntil102 = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 102, until = 102)
            val dataUntil102 = newsData(newsUntil102)
            val barcodesUntil102 = dataUntil102.map { it["barcode"] }
            assertEquals(barcodesUntilNow, barcodesUntil102)
            assertEquals(dataUntilNow, dataUntil102)

            // The requested 'until' param here is 1 sec before Now, and therefore the
            // returned news pieces are expected to not have the latest news piece, which
            // was made with time 102
            val newsUntil101 = requestNewsCmd(userToken, 1.1, 0.9, 0.9, 1.1, now = 102, until = 101)
            val dataUntil101 = newsData(newsUntil101)
            val barcodesUntil101 = dataUntil101.map { it["barcode"] }
            assertEquals(listOf(dataUntil102[1], dataUntil102[2]), dataUntil101)
            assertEquals(listOf(barcodesUntil102[1], barcodesUntil102[2]), barcodesUntil101)
        }
    }

    @Test
    fun `news from a banned and unbanned user`() {
        withPlanteTestApplication {
            val (userToken1, userId1) = registerAndGetTokenWithID(name = "Bob")
            val (userToken2, userId2) = registerAndGetTokenWithID(name = "Jake")

            val barcode1 = UUID.randomUUID().toString()
            val barcode2 = UUID.randomUUID().toString()
            val shop1 = generateFakeOsmUID()
            val shop2 = generateFakeOsmUID()

            putProductToShopCmd(userToken1, barcode1, shop1, lat = 1.0, lon = 1.0, now = 123)
            putProductToShopCmd(userToken2, barcode2, shop2, lat = 1.0, lon = 1.0, now = 124)

            var news = requestNewsCmd(userToken1, 1.1, 0.9, 0.9, 1.1, now = 125)
            assertEquals(2, news.size, news.toString())
            assertEquals(userId2, news[0]["creator_user_id"])
            assertEquals(userId1, news[1]["creator_user_id"])

            val moderator = registerModerator()
            banUserCmd(moderator, targetUserId = userId2)

            news = requestNewsCmd(userToken1, 1.1, 0.9, 0.9, 1.1, now = 125)
            assertEquals(1, news.size, news.toString())
            assertEquals(userId1, news[0]["creator_user_id"])

            banUserCmd(moderator, targetUserId = userId2, unban = true)

            news = requestNewsCmd(userToken1, 1.1, 0.9, 0.9, 1.1, now = 125)
            assertEquals(2, news.size, news.toString())
            assertEquals(userId2, news[0]["creator_user_id"])
            assertEquals(userId1, news[1]["creator_user_id"])
        }
    }
}
