package vegancheckteam.plante_server.osm

import io.ktor.server.testing.withTestApplication
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.module
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class OpenStreetMapTest {
    @Test
    fun `a very fragile requestShops test with REAL osm`() {
        withPlanteTestApplication {
            val shops = runBlocking {
                OpenStreetMap.requestShopsFor(
                    uids = listOf(
                        OsmUID.from(OsmElementType.WAY, "41186009"),
                        OsmUID.from(OsmElementType.WAY, "220871591"),
                        OsmUID.from(OsmElementType.NODE, "4450971294"),
                        OsmUID.from(OsmElementType.NODE, "1637412452"),
                    ),
                    httpClient = GlobalStorage.httpClient,
                )
            }.toSet()
            assertEquals(
                setOf(
                    OsmShop(
                        uid = OsmUID.from(OsmElementType.WAY, "41186009"),
                        lat = 50.8721113,
                        lon = 4.2968699,
                    ),
                    OsmShop(
                        uid = OsmUID.from(OsmElementType.WAY, "220871591"),
                        lat = 50.9978489,
                        lon = 3.882942,
                    ),
                    OsmShop(
                        uid = OsmUID.from(OsmElementType.NODE, "4450971294"),
                        lat = 56.3071606,
                        lon = 43.9984697,
                    ),
                    OsmShop(
                        uid = OsmUID.from(OsmElementType.NODE, "1637412452"),
                        lat = 56.3233611,
                        lon = 43.9864469,
                    ),
                ),
                shops
            )
        }
    }
}