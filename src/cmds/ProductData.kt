package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.from
import vegancheckteam.plante_server.db.select2
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.User

@Location("/product_data/")
data class ProductDataParams(val barcode: String)

fun productData(params: ProductDataParams, user: User): Any {
    val product = transaction {
        val existingProductRow = ProductTable.select2(by = user) { ProductTable.barcode eq params.barcode }.firstOrNull()
        existingProductRow?.let { Product.from(it) }
    }
    if (product != null) {
        return product
    }
    return GenericResponse.failure("product_not_found", "Barcode: ${params.barcode}")
}
