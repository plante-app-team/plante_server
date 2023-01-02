package vegancheckteam.plante_server.db

import java.sql.SQLException
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class SQLTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
                ProductPresenceVoteTable.deleteAll()
                ProductAtShopTable.deleteAll()
                ShopsValidationQueueTable.deleteAll()
                ShopTable.deleteAll()
                ProductChangeTable.deleteAll()
                ProductTable.deleteAll()
            }
        }
    }

    @Test
    fun `simple injection`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            transaction {
                ProductTable.insert {
                    it[ProductTable.barcode] = barcode
                    it[veganStatus] = VegStatus.UNKNOWN.persistentCode
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                }
            }

            var existsInDB = transaction {
                0 < ProductTable.select(where = ProductTable.barcode eq barcode).count()
            }
            assertTrue(existsInDB)

            transaction {
                val injection = "'$barcode'; DELETE FROM ${ProductTable.tableName};"
                val conn = TransactionManager.current().connection
                val statement = conn.prepareStatement(
                    "SELECT * FROM ${ProductTable.tableName} WHERE ${ProductTable.barcode.name} = $injection", false
                )
                try {
                    statement.executeQuery()
                } catch (e: SQLException){
                    // whatever
                }
            }

            // The injection is expected to be successful
            existsInDB = transaction {
                0 < ProductTable.select(where = ProductTable.barcode eq barcode).count()
            }
            assertFalse(existsInDB)
        }
    }

    @Test
    fun `simple injection with Kotlin Exposed`() {
        withPlanteTestApplication {
            val barcode = UUID.randomUUID().toString()
            transaction {
                ProductTable.insert {
                    it[ProductTable.barcode] = barcode
                    it[veganStatus] = VegStatus.UNKNOWN.persistentCode
                    it[veganStatusSource] = VegStatusSource.UNKNOWN.persistentCode
                }
            }

            var existsInDB = transaction {
                0 < ProductTable.select(where = ProductTable.barcode eq barcode).count()
            }
            assertTrue(existsInDB)

            transaction {
                val injection = "$barcode'; DELETE FROM ${ProductTable.tableName};"
                val whereStatement = ProductTable.barcode eq injection
                val conn = TransactionManager.current().connection
                val statement = conn.prepareStatement(
                    "SELECT * FROM ${ProductTable.tableName} WHERE $whereStatement", false
                )
                statement.executeQuery()
            }

            // The injection is expected to fail
            existsInDB = transaction {
                0 < ProductTable.select(where = ProductTable.barcode eq barcode).count()
            }
            assertTrue(existsInDB)
        }
    }
}