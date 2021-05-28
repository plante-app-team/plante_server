package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@Location("/put_product_to_shop/")
data class PutProductToShopParams(
    val barcode: String,
    val shopOsmId: String,
    val testingNow: Long? = null)

fun putProductToShop(params: PutProductToShopParams, user: User, testing: Boolean): GenericResponse {
    return transaction {
        val existingProduct = ProductTable.select { ProductTable.barcode eq params.barcode }.firstOrNull()
        val productId = if (existingProduct != null) {
            existingProduct[ProductTable.id]
        } else {
            val result = createUpdateProduct(CreateUpdateProductParams(params.barcode), user)
            if (result.error != null) {
                return@transaction result;
            }
            val newProduct = ProductTable.select { ProductTable.barcode eq params.barcode }.first()
            newProduct[ProductTable.id]
        }

        val now = now(params.testingNow, testing)
        val existingShop = ShopTable.select { ShopTable.osmId eq params.shopOsmId }.firstOrNull()
        val shopId = if (existingShop != null) {
            existingShop[ShopTable.id]
        } else {
            val inserted = ShopTable.insert {
                it[osmId] = params.shopOsmId
                it[creationTime] = now
                it[creatorUserId] = user.id
            }.resultedValues!![0]
            inserted[ShopTable.id]
        }

        val existingRow = ProductAtShopTable.select {
            (ProductAtShopTable.productId eq productId) and (ProductAtShopTable.shopId eq shopId)
        }.firstOrNull()
        if (existingRow != null) {
            // Already done!
            return@transaction GenericResponse.success()
        }

        // Insert product
        ProductAtShopTable.insert {
            it[ProductAtShopTable.productId] = productId
            it[ProductAtShopTable.shopId] = shopId
            it[creationTime] = now
        }
        // Increase products count
        ShopTable.update( { ShopTable.id eq shopId } ) {
            with(SqlExpressionBuilder) {
                it.update(productsCount, productsCount + 1)
            }
        }

        productPresenceVote(
            ProductPresenceVoteParams(params.barcode, params.shopOsmId, 1, params.testingNow),
            user,
            testing)

        GenericResponse.success()
    }
}
