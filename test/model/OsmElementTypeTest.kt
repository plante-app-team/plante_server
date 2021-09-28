package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class OsmElementTypeTest {
    @Test
    fun `persistent codes values`() {
        // Same persistent values are also used in the mobile client -
        // it's prohibited to change the values.
        assertEquals(1, OsmElementType.NODE.persistentCode)
        assertEquals(2, OsmElementType.RELATION.persistentCode)
        assertEquals(3, OsmElementType.WAY.persistentCode)
    }
}
