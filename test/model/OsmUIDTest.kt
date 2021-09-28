package vegancheckteam.plante_server.model

import io.ktor.server.testing.withTestApplication
import java.util.*
import kotlin.test.assertEquals
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import vegancheckteam.plante_server.module
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert.assertNotEquals

class OsmUIDTest {
    @Test
    fun `different element types parsing`() {
        val uid1 = OsmUID.from("1:12345")
        assertEquals(OsmUID.from(OsmElementType.NODE, "12345"), uid1)
        assertEquals(OsmElementType.NODE, uid1.elementType)
        assertEquals("12345", uid1.osmId)

        val uid2 = OsmUID.from("2:12345")
        assertEquals(OsmUID.from(OsmElementType.RELATION, "12345"), uid2)
        assertEquals(OsmElementType.RELATION, uid2.elementType)
        assertEquals("12345", uid2.osmId)

        val uid3 = OsmUID.from("3:12345")
        assertEquals(OsmUID.from(OsmElementType.WAY, "12345"), uid3)
        assertEquals(OsmElementType.WAY, uid3.elementType)
        assertEquals("12345", uid3.osmId)
    }

    @Test
    fun `toString()`() {
        val uid = OsmUID.from("1:12345")
        assertEquals("1:12345", uid.toString())
    }

    @Test
    fun `hashCode()`() {
        val uid1 = OsmUID.from("1:12345")
        val uid2 = OsmUID.from("1:12345")
        val uid3 = OsmUID.from("2:12345")
        assertEquals(uid1.hashCode(), uid2.hashCode())
        assertNotEquals(uid1.hashCode(), uid3.hashCode())
    }
}
