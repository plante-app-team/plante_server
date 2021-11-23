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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import vegancheckteam.plante_server.base.now

@Location("/create_update_product/")
data class CreateUpdateProductParams(
    val barcode: String,
    val vegetarianStatus: String? = null, // unused, but old clients still send
    val veganStatus: String? = null,
    val langs: List<String>? = null)

fun createUpdateProduct(params: CreateUpdateProductParams, user: User): GenericResponse {
    val veganStatus = params.veganStatus?.let { VegStatus.fromStringName(it) }
    if (veganStatus == null && params.veganStatus != null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.veganStatus}")
    }

    transaction {
        val existingProductRow = ProductTable.select { barcode eq params.barcode }.firstOrNull()
        val oldProduct = existingProductRow?.let { Product.from(it) }

        val productRow = if (oldProduct == null) {
            ProductTable.insert { row ->
                row[barcode] = params.barcode
                if (veganStatus != null) {
                    row[ProductTable.veganStatus] = veganStatus.persistentCode
                    row[veganStatusSource] = VegStatusSource.COMMUNITY.persistentCode
                }
                row[creatorUserId] = user.id
            }.resultedValues!![0]
        } else {
            if (oldProduct.veganStatusSource != VegStatusSource.MODERATOR) {
                if (veganStatus != null) {
                    ProductTable.update({ barcode eq params.barcode }) { row ->
                        row[ProductTable.veganStatus] = veganStatus.persistentCode
                        row[veganStatusSource] = VegStatusSource.COMMUNITY.persistentCode
                    }
                }
            }
            ProductTable.select { barcode eq params.barcode }.first()
        }

        val newProduct = Product.from(productRow)
        maybeInsertProductChangeInfo(oldProduct, newProduct, user)
        deleteExtraProductChanges(newProduct.barcode)
        val productChangeType = if (oldProduct != null && oldProduct != newProduct) {
            ProductChangeModerationTaskType.PRODUCT_CHANGE
        } else {
            val emptyProduct = emptyProductWith(newProduct.id, newProduct.barcode)
            if (emptyProduct != newProduct) {
                ProductChangeModerationTaskType.PRODUCT_CHANGE
            } else {
                ProductChangeModerationTaskType.PRODUCT_CHANGE_IN_OFF
            }
        }
        maybeCreateModeratorTasks(newProduct.barcode, user, params.langs, productChangeType)
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

private enum class ProductChangeModerationTaskType(val underlyingType: ModeratorTaskType) {
    PRODUCT_CHANGE(ModeratorTaskType.PRODUCT_CHANGE),
    PRODUCT_CHANGE_IN_OFF(ModeratorTaskType.PRODUCT_CHANGE_IN_OFF),
}

private fun maybeCreateModeratorTasks(barcode: String, user: User, langs: List<String>?, taskType: ProductChangeModerationTaskType) {
    if (langs != null && langs.isNotEmpty()) {
        for (lang in langs) {
            createModeratorTask(barcode, user, lang, taskType)
        }
    } else {
        createModeratorTask(barcode, user, null, taskType)
    }
}

private fun createModeratorTask(barcode: String, user: User, lang: String?, taskType: ProductChangeModerationTaskType) {
    val mainOp = (ModeratorTaskTable.productBarcode eq barcode) and (ModeratorTaskTable.lang eq lang)
    val existingTasksCounts = ProductChangeModerationTaskType.values().associateWith {
        ModeratorTaskTable.select(
            mainOp and (ModeratorTaskTable.taskType eq it.underlyingType.persistentCode)
        ).count()
    }

    val typesSortedByPriority = ProductChangeModerationTaskType.values().sortedBy { it.underlyingType.priority }
    for (type in typesSortedByPriority) {
        val existingTasksCount = existingTasksCounts[type] ?: 0
        if (existingTasksCount > 0 && type.underlyingType.priority <= taskType.underlyingType.priority) {
            // A task with a higher or equal priority already exists
            return
        }
    }

    // Delete tasks with lower priority for the product
    val sortedByPriorityReversed = typesSortedByPriority.reversed()
    for (type in sortedByPriorityReversed) {
        if (type == taskType) {
            break
        }
        val typeOp = (ModeratorTaskTable.taskType eq type.underlyingType.persistentCode)
        ModeratorTaskTable.deleteWhere(op = { mainOp and typeOp })
    }

    ModeratorTaskTable.insert {
        it[productBarcode] = barcode
        it[ModeratorTaskTable.taskType] = taskType.underlyingType.persistentCode
        it[taskSourceUserId] = user.id
        it[creationTime] = now()
        if (lang != null) {
            it[ModeratorTaskTable.lang] = lang
        }
    }
}

private fun emptyProductWith(id: Int, barcode: String): Product {
    return Product(id, barcode, null, null, null, null, null)
}
