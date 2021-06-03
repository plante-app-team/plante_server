package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.MAX_PRODUCT_CHANGES_COUNT
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.db.ProductChangeTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ProductTable.barcode
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource
import java.lang.Integer.max
import vegancheckteam.plante_server.base.now

@Location("/create_update_product/")
data class CreateUpdateProductParams(
    val barcode: String,
    val vegetarianStatus: String? = null,
    val veganStatus: String? = null)

fun createUpdateProduct(params: CreateUpdateProductParams, user: User): GenericResponse {
    val vegetarianStatus = params.vegetarianStatus?.let { VegStatus.fromStringName(it) }
    val veganStatus = params.veganStatus?.let { VegStatus.fromStringName(it) }
    if (vegetarianStatus == null && params.vegetarianStatus != null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.vegetarianStatus}")
    }
    if (veganStatus == null && params.veganStatus != null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.veganStatus}")
    }

    transaction {
        val existingProductRow = ProductTable.select { barcode eq params.barcode }.firstOrNull()
        val oldProduct = existingProductRow?.let { Product.from(it) }

        val productRow = if (oldProduct == null) {
            ProductTable.insert { row ->
                row[barcode] = params.barcode
                row[ProductTable.vegetarianStatus] = (vegetarianStatus ?: VegStatus.UNKNOWN).persistentCode
                row[vegetarianStatusSource] = VegStatusSource.COMMUNITY.persistentCode
                row[ProductTable.veganStatus] = (veganStatus ?: VegStatus.UNKNOWN).persistentCode
                row[veganStatusSource] = VegStatusSource.COMMUNITY.persistentCode
            }.resultedValues!![0]
        } else {
            ProductTable.update({ barcode eq params.barcode }) { row ->
                if (vegetarianStatus != null) {
                    row[ProductTable.vegetarianStatus] = vegetarianStatus.persistentCode
                    row[vegetarianStatusSource] = VegStatusSource.COMMUNITY.persistentCode
                }
                if (veganStatus != null) {
                    row[ProductTable.veganStatus] = veganStatus.persistentCode
                    row[veganStatusSource] = VegStatusSource.COMMUNITY.persistentCode
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
        it[time] = now()
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
                (ModeratorTaskTable.taskType eq ModeratorTaskType.PRODUCT_CHANGE.persistentCode)
    }
    ModeratorTaskTable.insert {
        it[productBarcode] = barcode
        it[taskType] = ModeratorTaskType.PRODUCT_CHANGE.persistentCode
        it[taskSourceUserId] = user.id
        it[creationTime] = now()
    }
}
