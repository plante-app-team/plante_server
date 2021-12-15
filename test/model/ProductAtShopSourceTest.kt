package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class ProductAtShopSourceTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(3, ProductAtShopSource.values().size)
        // Same persistent values are also used in the mobile client -
        // it's prohibited to change the values.
        assertEquals(1, ProductAtShopSource.MANUAL.persistentCode)
        assertEquals(2, ProductAtShopSource.OFF_SUGGESTION.persistentCode)
        assertEquals(3, ProductAtShopSource.RADIUS_SUGGESTION.persistentCode)
    }

    @Test
    fun `persistent names values`() {
        assertEquals(3, ProductAtShopSource.values().size)
        // Same persistent values are also used in the mobile client -
        // it's prohibited to change the values.
        assertEquals("manual", ProductAtShopSource.MANUAL.persistentName)
        assertEquals("off_suggestion", ProductAtShopSource.OFF_SUGGESTION.persistentName)
        assertEquals("radius_suggestion", ProductAtShopSource.RADIUS_SUGGESTION.persistentName)
    }

    @Test
    fun fromStr() {
        assertEquals(3, ProductAtShopSource.values().size)
        assertEquals(ProductAtShopSource.MANUAL, ProductAtShopSource.fromPersistentName("manual"))
        assertEquals(ProductAtShopSource.OFF_SUGGESTION, ProductAtShopSource.fromPersistentName("off_suggestion"))
        assertEquals(ProductAtShopSource.RADIUS_SUGGESTION, ProductAtShopSource.fromPersistentName("radius_suggestion"))
    }
}
