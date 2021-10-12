package vegancheckteam.plante_server

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import test_utils.generateFakeOsmUID
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ShopsInBoundsDataTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
            }
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
            val osmUidsWithCoords = mapOf(
                osmUids[0] to Pair(center.first, center.second), // Center
                osmUids[1] to Pair(southWestBounds.first - 0.1, center.second), // Too much west
                osmUids[2] to Pair(northEastBounds.first + 0.1, center.second), // Too much east
                osmUids[3] to Pair(center.first, southWestBounds.second - 0.1), // Too much south
                osmUids[4] to Pair(center.first, northEastBounds.second + 0.1), // Too much north
            )

            val clientToken = register()
            for (entry in osmUidsWithCoords.entries) {
                val osmUid = entry.key
                val lat = entry.value.first
                val lon = entry.value.second
                val map = authedGet(clientToken, "/put_product_to_shop/?", mapOf(
                    "barcode" to barcode,
                    "shopOsmUID" to osmUid.asStr,
                    "lat" to "$lat",
                    "lon" to "$lon",
                )).jsonMap()
                assertEquals("ok", map["result"])
            }
            val map = authedGet(clientToken, "/shops_in_bounds_data/?", mapOf(
                "north" to "${northEastBounds.first}",
                "south" to "${southWestBounds.first}",
                "west" to "${southWestBounds.second}",
                "east" to "${northEastBounds.second}",
            )).jsonMap()
            val shops = shopsFrom(map)
            assertTrue(shops.any { it["osm_uid"] == osmUids[0].asStr })
            assertFalse(shops.any { it["osm_uid"] == osmUids[1].asStr })
            assertFalse(shops.any { it["osm_uid"] == osmUids[2].asStr })
            assertFalse(shops.any { it["osm_uid"] == osmUids[3].asStr })
            assertFalse(shops.any { it["osm_uid"] == osmUids[4].asStr })
        }
    }

    @Test
    fun `normal area`() {
        areaTest(
            center = Pair(1.5, 1.5),
            northEastBounds = Pair(2.0, 2.0),
            southWestBounds = Pair(1.0, 1.0),
        )
    }

    @Test
    fun `england area`() {
        areaTest(
            center = Pair(0.0, 0.0),
            northEastBounds = Pair(1.0, 1.0),
            southWestBounds = Pair(-1.0, -1.0),
        )
    }

    @Test
    fun `fiji area`() {
        areaTest(
            center = Pair(10.0, 180.0),
            northEastBounds = Pair(11.0, -179.0),
            southWestBounds = Pair(9.0, 179.0),
        )
    }

    private fun tooBigAreaTest(
            northEastBounds: Pair<Double, Double>,
            southWestBounds: Pair<Double, Double>) {
        withPlanteTestApplication {
            val clientToken = register()
            val map = authedGet(clientToken, "/shops_in_bounds_data/?", mapOf(
                "north" to "${northEastBounds.first}",
                "south" to "${southWestBounds.first}",
                "west" to "${southWestBounds.second}",
                "east" to "${northEastBounds.second}",
            )).jsonMap()
            assertEquals("area_too_big", map["error"], map.toString())
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

    private fun shopsFrom(map: Map<*, *>): List<Map<*, *>> {
        val shops = map["results"] as Map<*, *>
        return shops.values.map { it as Map<*, *> }
    }
}
