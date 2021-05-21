package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource

@Location("/moderate_product_veg_statuses/")
data class ModerateProductVegStatusesParams(
    val barcode: String,
    val vegetarianStatus: String,
    val veganStatus: String)

fun moderateProductVegStatuses(params: ModerateProductVegStatusesParams, user: User): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val vegetarianStatus = VegStatus.fromStringName(params.vegetarianStatus)
    val veganStatus = VegStatus.fromStringName(params.veganStatus)
    if (vegetarianStatus == null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.vegetarianStatus}")
    }
    if (veganStatus == null) {
        return GenericResponse.failure("invalid_veg_status", "Provided status: ${params.veganStatus}")
    }

    val updated = transaction {
        ProductTable.update({ ProductTable.barcode eq params.barcode }) {
            it[ProductTable.vegetarianStatus] = vegetarianStatus.persistentCode
            it[vegetarianStatusSource] = VegStatusSource.MODERATOR.persistentCode
            it[ProductTable.veganStatus] = veganStatus.persistentCode
            it[veganStatusSource] = VegStatusSource.MODERATOR.persistentCode
        }
    }
    return if (updated > 0) {
        GenericResponse.success()
    } else {
        GenericResponse.failure("product_not_found", "Barcode: ${params.barcode}")
    }
}
