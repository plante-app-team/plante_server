package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/delete_shop_locally/")
data class DeleteShopLocallyParams(val shopOsmUID: String)

fun deleteShopLocally(params: DeleteShopLocallyParams, user: User): Any {
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

        if (0 < shop.productsCount) {
            return@transaction GenericResponse.failure("shop_has_products")
        } else if (!ProductAtShopTable.select(ProductAtShopTable.shopId eq shop.id).empty()) {
            Log.e("delete_shop_locally", "Deleted shops still has products in ProductAtShopTable")
            return@transaction GenericResponse.failure("shop_has_products")
        }

        ProductPresenceVoteTable.deleteWhere { ProductPresenceVoteTable.shopId eq shop.id }
        ShopsValidationQueueTable.deleteWhere { ShopsValidationQueueTable.shopId eq shop.id }
        ShopTable.update({ShopTable.id eq shop.id}) {
            it[deleted] = true
        }
        GenericResponse.success()
    }
}
