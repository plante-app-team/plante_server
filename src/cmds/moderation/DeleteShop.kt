package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/delete_shop_locally/")
data class DeleteShopParams(val shopOsmUID: String)

fun deleteShop(params: DeleteShopParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    return transaction {
        val shop = ShopTable.select(ShopTable.osmUID eq params.shopOsmUID)
            .map { Shop.from(it) }
            .firstOrNull()
        if (shop == null) {
            return@transaction GenericResponse.success()
        }

        ProductAtShopTable.deleteWhere { ProductAtShopTable.shopId eq shop.id }
        ProductPresenceVoteTable.deleteWhere { ProductPresenceVoteTable.shopId eq shop.id }
        ShopsValidationQueueTable.deleteWhere { ShopsValidationQueueTable.shopId eq shop.id }
        val deleted = ShopTable.deleteWhere { ShopTable.id eq shop.id }
        if (deleted > 0) {
            GenericResponse.success()
        } else {
            // Throwing exception to cancel the transaction
            throw IllegalStateException("Couldn't delete shop ${params.shopOsmUID}")
        }
    }
}
