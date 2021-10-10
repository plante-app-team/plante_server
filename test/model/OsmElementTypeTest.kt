package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class OsmElementTypeTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(3, OsmElementType.values().size)
        // Same persistent values are also used in the mobile client -
        // it's prohibited to change the values.
        assertEquals(1, OsmElementType.NODE.persistentCode)
        assertEquals(2, OsmElementType.RELATION.persistentCode)
        assertEquals(3, OsmElementType.WAY.persistentCode)
    }

    @Test
    fun fromStr() {
        assertEquals(3, OsmElementType.values().size)
        // These names are used by OSM so we must never change them
        assertEquals("node", OsmElementType.NODE.osmName)
        assertEquals("relation", OsmElementType.RELATION.osmName)
        assertEquals("way", OsmElementType.WAY.osmName)
    }
}
