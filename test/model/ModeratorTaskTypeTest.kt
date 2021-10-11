package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class ModeratorTaskTypeTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(4, ModeratorTaskType.values().size)
        // Same persistent values are also used in Web Admin -
        // it's prohibited to change the values.
        assertEquals(1, ModeratorTaskType.USER_REPORT.persistentCode)
        assertEquals(2, ModeratorTaskType.PRODUCT_CHANGE.persistentCode)
        assertEquals(3, ModeratorTaskType.OSM_SHOP_CREATION.persistentCode)
        assertEquals(4, ModeratorTaskType.OSM_SHOP_NEEDS_VALIDATION.persistentCode)
    }
}
