package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.ProductsAtShop
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.ProductsAtShopsResponse

@Location("/products_at_shops_data/")
data class ProductsAtShopsDataParams(val osmShopsIds: List<String>)

fun productsAtShopsData(params: ProductsAtShopsDataParams, user: User) = transaction {
    val selected = (ProductAtShopTable innerJoin ShopTable innerJoin ProductTable).select {
        ShopTable.osmId inList params.osmShopsIds
    }

    val shopsWithProducts = selected.map { Pair(Shop.from(it), Product.from(it)) }

    val result = mutableMapOf<String, ProductsAtShop>()
    for (shopWithProduct in shopsWithProducts) {
        if (shopWithProduct.first.osmId !in result) {
            result[shopWithProduct.first.osmId] = ProductsAtShop(
                shopWithProduct.first.osmId,
                mutableListOf(),
                mutableMapOf())
        }
        result[shopWithProduct.first.osmId]!!.products += shopWithProduct.second
    }

    val shopsIds = shopsWithProducts.map { it.first.id }.distinct()
    val latestPositiveVotes = ProductPresenceVoteTable.slice(
            ProductPresenceVoteTable.shopId,
            ProductPresenceVoteTable.productId,
            ProductPresenceVoteTable.voteTime).select {
        (ProductPresenceVoteTable.shopId inList shopsIds) and (ProductPresenceVoteTable.voteVal eq 1)
    }

    val osmShopsIdsMap = mutableMapOf<Int, String>()
    val barcodesMap = mutableMapOf<Int, String>()
    for (shopAndProduct in shopsWithProducts) {
        osmShopsIdsMap[shopAndProduct.first.id] = shopAndProduct.first.osmId
        barcodesMap[shopAndProduct.second.id] = shopAndProduct.second.barcode
    }
    val voteTimeAtShopAtProduct = mutableMapOf<String, MutableMap<String, Long>>()
    for (row in latestPositiveVotes) {
        val shopId = row[ProductPresenceVoteTable.shopId]
        val shopOsmId = osmShopsIdsMap[shopId]
        val productId = row[ProductPresenceVoteTable.productId]
        val barcode = barcodesMap[productId]
        val voteTime = row[ProductPresenceVoteTable.voteTime]
        if (shopOsmId == null || barcode == null) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            continue
        }
        if (shopOsmId !in result) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            continue
        }
        if (shopOsmId !in voteTimeAtShopAtProduct) {
            voteTimeAtShopAtProduct[shopOsmId] = mutableMapOf()
        }
        val productsLastSeen = result[shopOsmId]!!.productsLastSeen
        val latestVoteTime = productsLastSeen[barcode]
        if ((latestVoteTime ?: 0) < voteTime) {
            productsLastSeen[barcode] = voteTime
        }
    }

    ProductsAtShopsResponse(result)
}
