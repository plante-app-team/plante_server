package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductScanTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.base.now

const val PRODUCT_SCAN_STORAGE_DAYS_LIMIT = 7L

@Location("/product_scan/")
data class ProductScanParams(
    val barcode: String,
    val testingNow: Long? = null)

fun productScan(params: ProductScanParams, user: User, testing: Boolean): Any {
    val now = now(params.testingNow, testing)

    val maxTimeAgo = now - PRODUCT_SCAN_STORAGE_DAYS_LIMIT * 24 * 60 * 60
    transaction {
        ProductScanTable.deleteWhere {
            (ProductScanTable.productBarcode eq params.barcode) and
                    (ProductScanTable.time less maxTimeAgo)
        }
        ProductScanTable.insert {
            it[productBarcode] = params.barcode
            it[userId] = user.id
            it[time] = now
        }
    }
    return GenericResponse.success()
}
