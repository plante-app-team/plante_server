package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import java.time.ZonedDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User

const val MAX_PRODUCT_PRESENCE_VOTES_COUNT = 10
const val MIN_NEGATIVES_VOTES_FOR_DELETION = MAX_PRODUCT_PRESENCE_VOTES_COUNT / 2

@Location("/product_presence_vote/")
data class ProductPresenceVoteParams(
    val barcode: String,
    val shopOsmId: String,
    val voteVal: Int,
    val testingNow: Long? = null)

fun productPresenceVote(params: ProductPresenceVoteParams, user: User, testing: Boolean) = transaction {
    if (!arrayOf(0, 1).contains(params.voteVal)) {
        return@transaction GenericResponse.failure("invalid_vote_val", "Barcode: ${params.barcode}")
    }

    val productRow = ProductTable.select { ProductTable.barcode eq params.barcode }.firstOrNull()
    if (productRow == null) {
        return@transaction GenericResponse.failure("product_not_found", "Barcode: ${params.barcode}")
    }

    val shopRow = ShopTable.select { ShopTable.osmId eq params.shopOsmId }.firstOrNull()
    if (shopRow == null) {
        return@transaction GenericResponse.failure("shop_not_found", "OSM ID: ${params.shopOsmId}")
    }

    val now = if (params.testingNow != null && testing) {
        params.testingNow
    } else {
        ZonedDateTime.now().toEpochSecond()
    }
    val product = Product.from(productRow)
    val shop = Shop.from(shopRow)

    val productAtShop = ProductAtShopTable.select {
        (ProductAtShopTable.shopId eq shop.id) and (ProductAtShopTable.productId eq product.id)
    }.firstOrNull()
    if (productAtShop == null) {
        if (params.voteVal == 1) {
            // It will also create a vote
            return@transaction putProductToShop(
                PutProductToShopParams(
                    barcode = params.barcode,
                    shopOsmId = params.shopOsmId,
                    testingNow = params.testingNow
                ),
                user,
                testing
            );
        } else {
            return@transaction GenericResponse.success()
        }
    }

    ProductPresenceVoteTable.insert {
        it[productId] = product.id
        it[shopId] = shop.id
        it[votedUserId] = user.id
        it[voteTime] = now
        it[voteVal] = params.voteVal.toShort()
    }

    val latestProductVotesRows = ProductPresenceVoteTable.select {
        (ProductPresenceVoteTable.shopId eq shop.id) and (ProductPresenceVoteTable.productId eq product.id)
    }.orderBy(ProductPresenceVoteTable.voteTime, SortOrder.DESC)
    val latestProductVotes = latestProductVotesRows.map { it[ProductPresenceVoteTable.voteVal] }
    if (latestProductVotes.size < MAX_PRODUCT_PRESENCE_VOTES_COUNT) {
        return@transaction GenericResponse.success()
    }

    if (latestProductVotes.take(MIN_NEGATIVES_VOTES_FOR_DELETION).all { it == 0.toShort() }) {
        // Oopsie doopsie! The product is voted out!
        ProductAtShopTable.deleteWhere {
            (ProductAtShopTable.shopId eq shop.id) and (ProductAtShopTable.productId eq product.id)
        }
        ProductPresenceVoteTable.deleteWhere {
            (ProductPresenceVoteTable.shopId eq shop.id) and (ProductPresenceVoteTable.productId eq product.id)
        }
    } else {
        // Delete extra IDs
        val latestIds = latestProductVotesRows.map { it[ProductPresenceVoteTable.id] }
        val extraRowsCount = Integer.max(0, latestIds.size - MAX_PRODUCT_PRESENCE_VOTES_COUNT)
        val idsToDelete = latestIds.reversed().take(extraRowsCount)
        ProductPresenceVoteTable.deleteWhere {
            ProductPresenceVoteTable.id inList idsToDelete
        }
    }
    return@transaction GenericResponse.success()
}
