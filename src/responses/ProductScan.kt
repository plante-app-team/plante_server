package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.ProductScanTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User
import java.time.ZonedDateTime

const val PRODUCT_SCAN_STORAGE_DAYS_LIMIT = 7L

@Location("/product_scan/")
data class ProductScanParams(
    val barcode: String,
    val testingNow: Long? = null)

fun productScan(params: ProductScanParams, user: User, testing: Boolean): Any {
    val now = if (params.testingNow != null && testing) {
        params.testingNow
    } else {
        ZonedDateTime.now().toEpochSecond()
    }

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
