package vegancheckteam.plante_server.test_utils

import io.ktor.server.testing.TestApplicationEngine
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import vegancheckteam.plante_server.cmds.CreateShopTestingOsmResponses
import vegancheckteam.plante_server.cmds.moderation.MoveProductsDeleteShopTestingOsmResponses
import vegancheckteam.plante_server.model.OsmUID

fun TestApplicationEngine.requestNewsCmd(
    clientToken: String,
    north: Double,
    south: Double,
    west: Double,
    east: Double,
    page: Int = 0,
    now: Long? = null,
    until: Long? = null,
    expectedError: String? = null,
    expectedLastPage: Boolean? = null,
): List<Map<*, *>> {
    val params = mutableMapOf(
        "north" to north.toString(),
        "south" to south.toString(),
        "east" to east.toString(),
        "west" to west.toString(),
        "page" to page.toString(),
    )
    now?.let { params["testingNow"] = it.toString() }
    until?.let { params["untilSecsUtc"] = it.toString() }
    val map = authedGet(clientToken, "/news_data/", params).jsonMap()

    return if (expectedError == null) {
        assertNull(map["error"], map.toString())
        if (expectedLastPage != null) {
            assertEquals(expectedLastPage, map["last_page"], map.toString())
        }
        val result = map["results"] as List<*>
        result.map { it as Map<*, *> }
    } else {
        assertEquals(expectedError, map["error"], map.toString())
        emptyList()
    }
}

fun TestApplicationEngine.deleteNewsPieceCmd(id: Int, clientToken: String): Map<*, *> {
    return authedGet(clientToken, "/delete_news_piece/", mapOf(
        "newsPieceId" to id.toString(),
    )).jsonMap()
}

fun TestApplicationEngine.putProductToShopCmd(
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


fun TestApplicationEngine.createShopCmd(user: String, osmId: String, lat: Double, lon: Double) {
    val fakeOsmResponses = String(
        Base64.getEncoder().encode(
            CreateShopTestingOsmResponses("123456", osmId, "").toString().toByteArray()))
    val map = authedGet(user, "/create_shop/", mapOf(
        "lat" to lat.toString(),
        "lon" to lon.toString(),
        "name" to "myshop",
        "type" to "general",
        "testingResponsesJsonBase64" to fakeOsmResponses,
    )).jsonMap()
    assertEquals(osmId, map["osm_id"])
}

fun TestApplicationEngine.moveProductsDeleteShopCmd(
    user: String,
    badShop: OsmUID,
    goodShop: OsmUID,
    goodShopLat: Double,
    goodShopLon: Double) {
    val fakeOsmResponses = String(
        Base64.getEncoder().encode(
            MoveProductsDeleteShopTestingOsmResponses(
                "123456", "", "",
                goodShopFound = true,
                badShopFound = true,
                goodShopLat = goodShopLat,
                goodShopLon = goodShopLon,
            ).toString().toByteArray()))
    val map = authedGet(user, "/move_products_delete_shop/", mapOf(
        "badOsmUID" to badShop.asStr,
        "goodOsmUID" to goodShop.asStr,
        "testingResponsesJsonBase64" to fakeOsmResponses
    )).jsonMap()
    assertEquals("ok", map["result"], map.toString())
}

fun TestApplicationEngine.banUserCmd(
    moderator: String,
    targetUserId: String,
    expectedError: String? = null,
    unban: Boolean? = null,
) {
    val params = mutableMapOf(
        "userId" to targetUserId,
    )
    if (unban != null) {
        params["unban"] = "$unban"
    }
    val map = authedGet(moderator, "/user_ban/", params).jsonMap()
    return if (expectedError == null) {
        assertNull(map["error"], map.toString())
        assertEquals("ok", map["result"], map.toString())
    } else {
        assertEquals(expectedError, map["error"], map.toString())
    }
}

fun TestApplicationEngine.makeReportCmd(
    user: String,
    text: String,
    barcode: String? = null,
    newsPieceID: Int? = null,
    now: Long? = null,
    expectedError: String? = null,
) {
    val params = mutableMapOf(
        "text" to text,
    )
    barcode?.let { params["barcode"] = barcode }
    newsPieceID?.let { params["newsPieceId"] = "$it" }
    now?.let { params["testingNow"] = "$it" }

    val map = authedGet(user, "/make_report/", params).jsonMap()
    return if (expectedError == null) {
        assertNull(map["error"], map.toString())
        assertEquals("ok", map["result"], map.toString())
    } else {
        assertEquals(expectedError, map["error"], map.toString())
    }
}
