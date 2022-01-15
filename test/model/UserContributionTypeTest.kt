package vegancheckteam.plante_server.model

import kotlin.test.assertEquals
import org.junit.Test

class UserContributionTypeTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(4, UserContributionType.values().size)
        // Same persistent values are also used in the mobile client -
        // it's prohibited to change the values.
        assertEquals(1, UserContributionType.PRODUCT_EDITED.persistentCode)
        assertEquals(2, UserContributionType.PRODUCT_ADDED_TO_SHOP.persistentCode)
        assertEquals(3, UserContributionType.PRODUCT_REPORTED.persistentCode)
        assertEquals(4, UserContributionType.SHOP_CREATED.persistentCode)
    }
}
