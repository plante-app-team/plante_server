package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.cmds.model.ProductsAtShopsResponse

@Location("/products_at_shops_data/")
data class ProductsAtShopsDataParams(val osmShopsIds: List<String>)

fun productsAtShopsData(params: ProductsAtShopsDataParams, user: User) = transaction {
    val selected = (ProductAtShopTable innerJoin ShopTable innerJoin ProductTable).select {
        ShopTable.osmId inList params.osmShopsIds
    }

    val shopsWithProducts = selected.map { Pair(Shop.from(it), Product.from(it)) }

    val results = mutableMapOf<String, MutableList<Product>>()
    for (shopWithProduct in shopsWithProducts) {
        if (shopWithProduct.first.osmId !in results) {
            results[shopWithProduct.first.osmId] = mutableListOf()
        }
        results[shopWithProduct.first.osmId]!! += shopWithProduct.second;
    }

    ProductsAtShopsResponse(results)
}
