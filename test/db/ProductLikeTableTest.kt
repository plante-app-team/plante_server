package vegancheckteam.plante_server.db

import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ProductLikeTableTest {
    @Test
    fun `a combination of a barcode and a user is unique`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            val (_, user1Str) = registerAndGetTokenWithID(name = "Bob")
            val (_, user2Str) = registerAndGetTokenWithID(name = "Jack")
            val user1 = UUID.fromString(user1Str)
            val user2 = UUID.fromString(user2Str)

            val inserted1 = transaction {
                val values = ProductLikeTable.insert {
                    it[ProductLikeTable.barcode] = barcode
                    it[userId] = user1
                    it[time] = 123L
                }.resultedValues
                values == null || values.isNotEmpty()
            }
            assertTrue(inserted1)

            val inserted2 = transaction {
                val values = ProductLikeTable.insert {
                    it[ProductLikeTable.barcode] = barcode
                    it[userId] = user2
                    it[time] = 123L
                }.resultedValues
                values == null || values.isNotEmpty()
            }
            assertTrue(inserted2)

            // User1 again
            val inserted3 = transaction {
                try {
                    val values = ProductLikeTable.insert {
                        it[ProductLikeTable.barcode] = barcode
                        it[userId] = user1
                        it[time] = 123L
                    }.resultedValues
                    values == null || values.isNotEmpty()
                } catch (e: Throwable) {
                    false
                }
            }
            assertFalse(inserted3)
        }
    }
}