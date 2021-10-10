package vegancheckteam.plante_server.db

import java.util.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
                    it[vegetarianStatus] = VegStatus.UNKNOWN.persistentCode
                    it[vegetarianStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                    it[veganStatus] = VegStatus.UNKNOWN.persistentCode
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                }.resultedValues
                inserted1 = values1 == null || values1.isNotEmpty()
            }
            try {
                transaction {
                    val values2 = ProductTable.insert {
                        it[ProductTable.barcode] = barcode
                        it[vegetarianStatus] = VegStatus.POSITIVE.persistentCode
                        it[vegetarianStatusSource] = VegStatusSource.COMMUNITY.persistentCode
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
}
