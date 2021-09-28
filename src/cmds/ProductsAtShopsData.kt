package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import kotlin.collections.Map.Entry
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.ProductsAtShop
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.ProductsAtShopsResponse

@Location("/products_at_shops_data/")
data class ProductsAtShopsDataParams(
    val osmShopsIds: List<String>? = null,
    val osmShopsUIDs: List<String>? = null)

fun productsAtShopsData(params: ProductsAtShopsDataParams) = transaction {
    val uids = if (params.osmShopsUIDs != null) {
        params.osmShopsUIDs.map { OsmUID.from(it) }
    } else if (params.osmShopsIds != null) {
        params.osmShopsIds.map { OsmUID.from(OsmElementType.NODE, it) }
    } else {
        return@transaction GenericResponse.failure("wtf")
    }

    val selected = (ProductAtShopTable innerJoin ShopTable innerJoin ProductTable).select {
        ShopTable.osmUID inList uids.map { it.asStr }
    }

    val shopsWithProducts = selected.map { Pair(Shop.from(it), Product.from(it)) }

    val result = mutableMapOf<OsmUID, ProductsAtShop>()
    for (shopWithProduct in shopsWithProducts) {
        if (shopWithProduct.first.osmUID !in result) {
            result[shopWithProduct.first.osmUID] = ProductsAtShop(
                shopWithProduct.first.osmId,
                shopWithProduct.first.osmUID,
                mutableListOf(),
                mutableMapOf())
        }
        result[shopWithProduct.first.osmUID]!!.products += shopWithProduct.second
    }

    val shopsIds = shopsWithProducts.map { it.first.id }.distinct()
    val latestPositiveVotes = ProductPresenceVoteTable.slice(
            ProductPresenceVoteTable.shopId,
            ProductPresenceVoteTable.productId,
            ProductPresenceVoteTable.voteTime).select {
        (ProductPresenceVoteTable.shopId inList shopsIds) and (ProductPresenceVoteTable.voteVal eq 1)
    }

    val osmShopsIdsMap = mutableMapOf<Int, OsmUID>()
    val barcodesMap = mutableMapOf<Int, String>()
    for (shopAndProduct in shopsWithProducts) {
        osmShopsIdsMap[shopAndProduct.first.id] = shopAndProduct.first.osmUID
        barcodesMap[shopAndProduct.second.id] = shopAndProduct.second.barcode
    }
    val voteTimeAtShopAtProduct = mutableMapOf<OsmUID, MutableMap<String, Long>>()
    for (row in latestPositiveVotes) {
        val shopId = row[ProductPresenceVoteTable.shopId]
        val shopOsmUID = osmShopsIdsMap[shopId]
        val productId = row[ProductPresenceVoteTable.productId]
        val barcode = barcodesMap[productId]
        val voteTime = row[ProductPresenceVoteTable.voteTime]
        if (shopOsmUID == null || barcode == null) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            continue
        }
        if (shopOsmUID !in result) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            continue
        }
        if (shopOsmUID !in voteTimeAtShopAtProduct) {
            voteTimeAtShopAtProduct[shopOsmUID] = mutableMapOf()
        }
        val productsLastSeen = result[shopOsmUID]!!.productsLastSeen
        val latestVoteTime = productsLastSeen[barcode]
        if ((latestVoteTime ?: 0) < voteTime) {
            productsLastSeen[barcode] = voteTime
        }
    }

    ProductsAtShopsResponse(results = result.values.associateBy { it.shopOsmId }, resultsV2 = result)
}
