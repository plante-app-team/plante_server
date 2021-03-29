package vegancheckteam.untitled_vegan_app_server.db

import io.ktor.server.testing.withTestApplication
import java.util.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.postgresql.util.PSQLException
import vegancheckteam.untitled_vegan_app_server.model.VegStatus
import vegancheckteam.untitled_vegan_app_server.model.VegStatusSource
import vegancheckteam.untitled_vegan_app_server.module
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductTableTest {
    @Test
    fun `barcodes are unique`() {
        withTestApplication({ module(testing = true) }) {
            val barcode = UUID.randomUUID().toString()
            var inserted1: Boolean? = null
            var inserted2: Boolean? = null
            transaction {
                val values1 = ProductTable.insert {
                    it[ProductTable.barcode] = barcode
                    it[vegetarianStatus] = VegStatus.UNKNOWN.name
                    it[vegetarianStatusSource] = VegStatusSource.UNKNOWN.name
                    it[veganStatus] = VegStatus.UNKNOWN.name
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.name
                }.resultedValues
                inserted1 = values1 == null || values1.isNotEmpty()
            }
            try {
                transaction {
                    val values2 = ProductTable.insert {
                        it[ProductTable.barcode] = barcode
                        it[vegetarianStatus] = VegStatus.POSITIVE.name
                        it[vegetarianStatusSource] = VegStatusSource.COMMUNITY.name
                        it[veganStatus] = VegStatus.POSITIVE.name
                        it[veganStatusSource] = VegStatusSource.COMMUNITY.name
                    }.resultedValues
                    inserted2 = values2 == null || values2.isNotEmpty()
                }
            } catch (e: ExposedSQLException) {
                if (!(e.message ?: "").contains("unique constraint")) {
                    throw e;
                }
                inserted2 = false
            }
            assertTrue(inserted1!!)
            assertFalse(inserted2!!)
        }
    }
}
