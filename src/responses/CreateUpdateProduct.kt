package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import java.time.ZonedDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.MAX_PRODUCT_CHANGES_COUNT
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskType
import vegancheckteam.untitled_vegan_app_server.db.ProductChangeTable
import vegancheckteam.untitled_vegan_app_server.db.ProductTable
import vegancheckteam.untitled_vegan_app_server.db.ProductTable.barcode
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.Product
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.VegStatus
import vegancheckteam.untitled_vegan_app_server.model.VegStatusSource
import java.lang.Integer.max

@Location("/create_update_product/")
data class CreateUpdateProductParams(
    val barcode: String,
    val vegetarianStatus: String? = null,
    val veganStatus: String? = null)

fun createUpdateProduct(params: CreateUpdateProductParams, user: User): Any {
    val vegetarianStatus = params.vegetarianStatus?.let { VegStatus.fromStringName(it) }
    val veganStatus = params.veganStatus?.let { VegStatus.fromStringName(it) }
    if (vegetarianStatus == null && params.vegetarianStatus != null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.vegetarianStatus}")
    }
    if (veganStatus == null && params.veganStatus != null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.vegetarianStatus}")
    }

    transaction {
        val existingProductRow = ProductTable.select { barcode eq params.barcode }.firstOrNull()
        val oldProduct = existingProductRow?.let { Product.from(it) }

        val productRow = if (oldProduct == null) {
            ProductTable.insert { row ->
                row[barcode] = params.barcode
                row[ProductTable.vegetarianStatus] = (vegetarianStatus ?: VegStatus.UNKNOWN).statusName
                row[vegetarianStatusSource] = VegStatusSource.COMMUNITY.sourceName
                row[ProductTable.veganStatus] = (veganStatus ?: VegStatus.UNKNOWN).statusName
                row[veganStatusSource] = VegStatusSource.COMMUNITY.sourceName
            }.resultedValues!![0]
        } else {
            ProductTable.update { row ->
                if (vegetarianStatus != null) {
                    row[ProductTable.vegetarianStatus] = vegetarianStatus.statusName
                    row[vegetarianStatusSource] = VegStatusSource.COMMUNITY.sourceName
                }
                if (veganStatus != null) {
                    row[ProductTable.veganStatus] = veganStatus.statusName
                    row[veganStatusSource] = VegStatusSource.COMMUNITY.sourceName
                }
            }
            ProductTable.select { barcode eq params.barcode }.first()
        }
        val newProduct = Product.from(productRow)
        maybeInsertProductChangeInfo(oldProduct, newProduct, user)
        deleteExtraProductChanges(newProduct.barcode)
        maybeCreateModeratorTask(newProduct.barcode, user)
    }
    return GenericResponse.success()
}

private fun maybeInsertProductChangeInfo(oldProduct: Product?, newProduct: Product, editor: User) {
    val oldProductJsonVal = oldProduct?.toString() ?: "{}"
    val newProductJsonVal = newProduct.toString()
    if (oldProductJsonVal == newProductJsonVal) {
        return
    }
    ProductChangeTable.insert {
        it[editorId] = editor.id
        it[productBarcode] = newProduct.barcode
        it[time] = ZonedDateTime.now().toEpochSecond()
        it[oldProductJson] = oldProductJsonVal
        it[newProductJson] = newProductJsonVal
    }
}

private fun deleteExtraProductChanges(barcode: String) {
    val rows = ProductChangeTable.select {
        ProductChangeTable.productBarcode eq barcode
    }.orderBy(ProductChangeTable.time, order = SortOrder.ASC)

    val ids = rows.map { it[ProductChangeTable.id] }
    val extraRowsCount = max(0, ids.size - MAX_PRODUCT_CHANGES_COUNT)
    val idsToDelete = ids.take(extraRowsCount)
    ProductChangeTable.deleteWhere {
        ProductChangeTable.id inList idsToDelete
    }
}

fun maybeCreateModeratorTask(barcode: String, user: User) {
    // NOTE: we create a moderator task even if product change was not inserted -
    // that is because not all product changes happen on this server, some happen on OFF.
    ModeratorTaskTable.deleteWhere {
        (ModeratorTaskTable.productBarcode eq barcode) and
                (ModeratorTaskTable.taskType eq ModeratorTaskType.PRODUCT_CHANGE.persistentId)
    }
    ModeratorTaskTable.insert {
        it[productBarcode] = barcode
        it[taskType] = ModeratorTaskType.PRODUCT_CHANGE.persistentId
        it[taskSourceUserId] = user.id
        it[time] = ZonedDateTime.now().toEpochSecond()
    }
}
