package cmds.moderation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.from
import vegancheckteam.plante_server.db.select2
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/product_presence_votes_data/")
data class ProductPresenceVotesDataParams(
    val barcode: String? = null,
    val shopOsmId: String? = null,
    val shopOsmUID: String? = null)

fun productPresenceVotesData(params: ProductPresenceVotesDataParams, user: User) = transaction {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return@transaction GenericResponse.failure("denied")
    }

    val product = if (params.barcode != null) {
        val row = ProductTable.select2(by = user) { ProductTable.barcode eq params.barcode }.firstOrNull()
        if (row == null) {
            return@transaction ProductPresenceVotesDataResponse(votes = emptyList())
        } else {
            Product.from(row)
        }
    } else {
        null
    }
    val shop = if (params.shopOsmUID != null || params.shopOsmId != null) {
        val uid = OsmUID.fromEitherOf(params.shopOsmUID, params.shopOsmId)
        if (uid == null) {
            return@transaction GenericResponse.failure("wtf")
        }
        val row = ShopTable.select { ShopTable.osmUID eq uid.asStr }.firstOrNull()
        if (row == null) {
            return@transaction ProductPresenceVotesDataResponse(votes = emptyList())
        } else {
            Shop.from(row)
        }
    } else {
        null
    }

    if (product == null && shop == null) {
        return@transaction ProductPresenceVotesDataResponse(votes = emptyList())
    }

    val votes = (ProductPresenceVoteTable innerJoin ProductTable innerJoin ShopTable) .select {
        val productQuery = if (product != null) {
            ProductPresenceVoteTable.productId eq product.id
        } else {
            Op.TRUE
        }
        val shopQuery = if (shop != null) {
            ProductPresenceVoteTable.shopId eq shop.id
        } else {
            Op.TRUE
        }
        productQuery and shopQuery
    }.orderBy(ProductPresenceVoteTable.voteTime, SortOrder.DESC)
        .map { ProductPresenceVote.from(it) }
    return@transaction ProductPresenceVotesDataResponse(votes)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class ProductPresenceVote(
    @JsonProperty("barcode")
    val barcode: String,
    @Deprecated("use shopOsmUID")
    @JsonProperty("shop_osm_id")
    val shopOsmId: String,
    @JsonProperty("shop_osm_uid")
    val shopOsmUID: OsmUID,
    @JsonProperty("voted_user_id")
    val votedUserId: String,
    @JsonProperty("vote_time")
    val voteTime: Long,
    @JsonProperty("vote_val")
    val voteVal: Short) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        fun from(tableRow: ResultRow): ProductPresenceVote {
            val uid = OsmUID.from(tableRow[ShopTable.osmUID])
            return ProductPresenceVote(
                barcode = tableRow[ProductTable.barcode],
                shopOsmId = uid.osmId,
                shopOsmUID = uid,
                votedUserId = tableRow[ProductPresenceVoteTable.votedUserId].toString(),
                voteTime = tableRow[ProductPresenceVoteTable.voteTime],
                voteVal = tableRow[ProductPresenceVoteTable.voteVal])
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class ProductPresenceVotesDataResponse(
    @JsonProperty("votes")
    val votes: List<ProductPresenceVote>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
