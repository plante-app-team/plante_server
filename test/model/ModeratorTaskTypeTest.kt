package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class ModeratorTaskTypeTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(8, ModeratorTaskType.values().size)
        // Same persistent values are also used in Web Admin -
        // it's prohibited to change the values.
        assertEquals(1, ModeratorTaskType.USER_PRODUCT_REPORT.persistentCode)
        assertEquals(2, ModeratorTaskType.PRODUCT_CHANGE.persistentCode)
        assertEquals(3, ModeratorTaskType.OSM_SHOP_CREATION.persistentCode)
        assertEquals(4, ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION.persistentCode)
        assertEquals(5, ModeratorTaskType.PRODUCT_CHANGE_IN_OFF.persistentCode)
        assertEquals(6, ModeratorTaskType.USER_FEEDBACK.persistentCode)
        assertEquals(7, ModeratorTaskType.USER_NEWS_PIECE_REPORT.persistentCode)
        assertEquals(100, ModeratorTaskType.CUSTOM_MODERATION_ACTION.persistentCode)
    }
}
