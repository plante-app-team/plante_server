package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.ProductTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.Product
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/product_data/")
data class ProductDataParams(val barcode: String)

fun productData(params: ProductDataParams, user: User): Any {
    val product = transaction {
        val existingProductRow = ProductTable.select { ProductTable.barcode eq params.barcode }.firstOrNull()
        existingProductRow?.let { Product.from(it) }
    }
    if (product != null) {
        return product
    }
    return GenericResponse.failure("product_not_found", "Barcode: ${params.barcode}")
}
