package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.ProductChangeTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.ProductChange
import vegancheckteam.untitled_vegan_app_server.model.ProductsChangesList
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup

@Location("/product_changes_data/")
data class ProductChangesDataParams(val barcode: String)

fun productChangesData(params: ProductChangesDataParams, user: User): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
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
