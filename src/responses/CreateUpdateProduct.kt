package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import java.time.ZonedDateTime
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.ProductChangeTable
import vegancheckteam.untitled_vegan_app_server.db.ProductTable
import vegancheckteam.untitled_vegan_app_server.db.ProductTable.barcode
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.Product
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.VegStatus
import vegancheckteam.untitled_vegan_app_server.model.VegStatusSource

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
        insertProductChangeInfo(oldProduct, Product.from(productRow), user)
    }
    return GenericResponse.success()
}

private fun insertProductChangeInfo(oldProduct: Product?, newProduct: Product, editor: User)
        = ProductChangeTable.insert {
    it[editorId] = editor.id
    it[productBarcode] = newProduct.barcode
    it[time] = ZonedDateTime.now().toEpochSecond()
    it[oldProductJson] = oldProduct?.toString() ?: "{}"
    it[newProductJson] = newProduct.toString()
}
