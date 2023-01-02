package vegancheckteam.plante_server.db

import java.util.*
import kotlin.test.assertEquals
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.test_utils.registerAndGetTokenWithID
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class ProductTableTest {
    @Test
    fun `barcodes are unique`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            var inserted1: Boolean? = null
            var inserted2: Boolean? = null
            transaction {
                val values1 = ProductTable.insert {
                    it[ProductTable.barcode] = barcode
                    it[veganStatus] = VegStatus.UNKNOWN.persistentCode
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                }.resultedValues
                inserted1 = values1 == null || values1.isNotEmpty()
            }
            try {
                transaction {
                    val values2 = ProductTable.insert {
                        it[ProductTable.barcode] = barcode
                        it[veganStatus] = VegStatus.POSITIVE.persistentCode
                        it[veganStatusSource] = VegStatusSource.COMMUNITY.persistentCode
                    }.resultedValues
                    inserted2 = values2 == null || values2.isNotEmpty()
                }
            } catch (e: ExposedSQLException) {
                if (!(e.message ?: "").contains("unique constraint")) {
                    throw e
                }
                inserted2 = false
            }
            assertTrue(inserted1!!)
            assertFalse(inserted2!!)
        }
    }

    @Test
    fun `products are selected with likes`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            val (_, user1Str) = registerAndGetTokenWithID(name = "Bob")
            val (_, user2Str) = registerAndGetTokenWithID(name = "Jack")
            val user1 = UUID.fromString(user1Str)
            val user2 = UUID.fromString(user2Str)

            // Put the product into DB
            transaction {
                ProductTable.insert {
                    it[ProductTable.barcode] = barcode
                    it[veganStatus] = VegStatus.UNKNOWN.persistentCode
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                }
            }

            // Ensure product's initial state
            var product = transaction {
                val row = ProductTable.select2(by = user1) {
                    ProductTable.barcode eq barcode
                }.first()
               Product.from(row)
            }
            assertEquals(barcode, product.barcode)
            assertEquals(0, product.likesCount)
            assertEquals(false, product.likedByMe)

            // Insert a like
            transaction {
                ProductLikeTable.insert {
                    it[ProductLikeTable.userId] = user2
                    it[ProductLikeTable.barcode] = barcode
                    it[ProductLikeTable.time] = 123
                }
            }
            // Check the first like
            product = transaction {
                val row = ProductTable.select2(by = user1) {
                    ProductTable.barcode eq barcode
                }.first()
                Product.from(row)
            }
            assertEquals(barcode, product.barcode)
            assertEquals(1, product.likesCount)
            assertEquals(false, product.likedByMe)

            // Insert a second like
            transaction {
                ProductLikeTable.insert {
                    it[ProductLikeTable.userId] = user1
                    it[ProductLikeTable.barcode] = barcode
                    it[ProductLikeTable.time] = 123
                }
            }
            // Check the second like
            product = transaction {
                val row = ProductTable.select2(by = user1) {
                    ProductTable.barcode eq barcode
                }.first()
                Product.from(row)
            }
            assertEquals(barcode, product.barcode)
            assertEquals(2, product.likesCount)
            assertEquals(true, product.likedByMe)
        }
    }
}
