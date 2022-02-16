package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class ShopValidationReasonTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(4, ShopValidationReason.values().size)
        // Persistent values are stored in DB and thus must never change
        assertEquals(1, ShopValidationReason.COORDS_WERE_NULL.persistentCode)
        assertEquals(2, ShopValidationReason.NEVER_VALIDATED_BEFORE.persistentCode)
        assertEquals(3, ShopValidationReason.SHOP_MOVED.persistentCode)
        assertEquals(4, ShopValidationReason.PERIODIC_REVALIDATION.persistentCode)
    }
}
