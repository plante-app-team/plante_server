package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/clear_product_veg_statuses/")
data class ClearProductVegStatusesParams(
    val barcode: String)

fun clearProductVegStatuses(params: ClearProductVegStatusesParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    transaction {
        ProductTable.update({ ProductTable.barcode eq params.barcode }) {
            it[vegetarianStatus] = null
            it[vegetarianStatusSource] = null
            it[veganStatus] = null
            it[veganStatusSource] = null
        }
    }
    return GenericResponse.success()
}
