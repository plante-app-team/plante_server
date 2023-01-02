package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.deepDeleteNewsForBarcode
import vegancheckteam.plante_server.db.from
import vegancheckteam.plante_server.db.select2
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User

const val MIN_NEGATIVES_VOTES_FOR_DELETION = 3

@Location("/product_presence_vote/")
data class ProductPresenceVoteParams(
    val barcode: String,
    val shopOsmId: String? = null,
    val shopOsmUID: String? = null,
    val voteVal: Int,
    val testingNow: Long? = null)

/**
 * Response might include a boolean "deleted" field.
 * The field is guaranteed to be present only if the product is voted out.
 */
fun productPresenceVote(params: ProductPresenceVoteParams, user: User, testing: Boolean): Any = transaction {
    val osmUID = OsmUID.fromEitherOf(params.shopOsmUID, params.shopOsmId)
    if (osmUID == null) {
        return@transaction GenericResponse.failure("wtf")
    }

    if (!arrayOf(0, 1).contains(params.voteVal)) {
        return@transaction GenericResponse.failure("invalid_vote_val", "Barcode: ${params.barcode}")
    }

    val productRow = ProductTable.select2(by = user) { ProductTable.barcode eq params.barcode }.firstOrNull()
    if (productRow == null) {
        return@transaction GenericResponse.failure("product_not_found", "Barcode: ${params.barcode}")
    }

    val shopRow = ShopTable.select { ShopTable.osmUID eq osmUID.asStr }.firstOrNull()
    if (shopRow == null) {
        return@transaction GenericResponse.failure("shop_not_found", "OSM UID: $osmUID")
    }
    if (shopRow[ShopTable.deleted]) {
        if (params.voteVal == 1) {
            return@transaction GenericResponse.failure("shop_deleted", "OSM UID: $osmUID")
        } else {
            // The shop is deleted and the vote was to delete a product from it - sure, deleted
            return@transaction ProductPresenceVoteResponse(deleted = true)
        }
    }

    val now = now(params.testingNow, testing)
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
                    shopOsmUID = osmUID.asStr,
                    testingNow = params.testingNow
                ),
                user,
                testing
            )
        } else {
            return@transaction ProductPresenceVoteResponse(deleted = true)
        }
    }

    // Delete all existing votes of this user for/against this product in this shop
    ProductPresenceVoteTable.deleteWhere {
        (ProductPresenceVoteTable.productId eq product.id) and
                (ProductPresenceVoteTable.shopId eq shop.id) and
                (ProductPresenceVoteTable.votedUserId eq user.id)
    }

    // Vote!
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
        .toList()
    val latestProductVotes = latestProductVotesRows.map { it[ProductPresenceVoteTable.voteVal] }

    val votedOutByQuantity = MIN_NEGATIVES_VOTES_FOR_DELETION <= latestProductVotes.size
            && latestProductVotes.take(MIN_NEGATIVES_VOTES_FOR_DELETION).all { it == 0.toShort() }
    val votedOutByUser = params.voteVal == 0 && productAtShop[ProductAtShopTable.creatorUserId] == user.id

    val deleted: Boolean
    if (votedOutByQuantity || votedOutByUser) {
        // Oopsie doopsie! The product is voted out!
        ProductAtShopTable.deleteWhere {
            (ProductAtShopTable.shopId eq shop.id) and (ProductAtShopTable.productId eq product.id)
        }
        ProductPresenceVoteTable.deleteWhere {
            (ProductPresenceVoteTable.shopId eq shop.id) and (ProductPresenceVoteTable.productId eq product.id)
        }
        NewsPieceProductAtShopTable.deepDeleteNewsForBarcode(product.barcode)
        val productsCountValue = ProductAtShopTable.countAcceptableProducts(shop.id)
        ShopTable.update( { ShopTable.id eq shop.id } ) {
            it[productsCount] = productsCountValue.toInt()
        }
        deleted = true
    } else {
        // Delete extra votes
        val latestIds = latestProductVotesRows.map { it[ProductPresenceVoteTable.id] }
        val extraRowsCount = Integer.max(0, latestIds.size - MIN_NEGATIVES_VOTES_FOR_DELETION)
        val idsToDelete = latestIds.reversed().take(extraRowsCount)
        ProductPresenceVoteTable.deleteWhere {
            ProductPresenceVoteTable.id inList idsToDelete
        }
        deleted = false
    }
    return@transaction ProductPresenceVoteResponse(deleted)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductPresenceVoteResponse(
    @JsonProperty("deleted")
    val deleted: Boolean,
    @JsonProperty(GenericResponse.RESULT_FIELD_NAME)
    val result: String = GenericResponse.RESULT_FIELD_OK_VAL) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
