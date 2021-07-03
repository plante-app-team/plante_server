package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductChangeTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ProductChange
import vegancheckteam.plante_server.model.ProductsChangesList
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/product_changes_data/")
data class ProductChangesDataParams(val barcode: String)

fun productChangesData(params: ProductChangesDataParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    val changes = transaction {
        val changesRows = ProductChangeTable.select {
            ProductChangeTable.productBarcode eq params.barcode
        }.orderBy(ProductChangeTable.time, order = SortOrder.ASC)
        changesRows.map { ProductChange.from(it) }
    }
    return ProductsChangesList(changes)
}
