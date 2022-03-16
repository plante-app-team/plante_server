package vegancheckteam.plante_server.model.news

import kotlin.test.assertEquals
import org.junit.Test

class NewsPieceTypeTest {
    @Test
    fun `persistent codes values`() {
        assertEquals(1, NewsPieceType.values().size)
        // Same persistent values are also used in the app -
        // it's prohibited to change the values.
        assertEquals(1, NewsPieceType.PRODUCT_AT_SHOP.persistentCode)
    }
}
