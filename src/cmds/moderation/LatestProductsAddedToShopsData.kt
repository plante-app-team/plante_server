package vegancheckteam.plante_server.cmds.moderation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.from
import vegancheckteam.plante_server.db.select2
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/latest_products_added_to_shops_data/")
data class LatestProductsAddedToShopsDataParams(
    val limit: Int = 100,
    val page: Long = 0)

fun latestProductsAddedToShopsData(params: LatestProductsAddedToShopsDataParams, user: User) = transaction {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return@transaction GenericResponse.failure("denied")
    }

    val productsAtShopsRows = ProductAtShopTable
        .selectAll()
        .orderBy(ProductAtShopTable.creationTime, SortOrder.DESC)
        .limit(params.limit, params.limit * params.page)
        .toList()

    val shopsIds = productsAtShopsRows.map { it[ProductAtShopTable.shopId] }
    val productsIds = productsAtShopsRows.map { it[ProductAtShopTable.productId] }

    val shops = ShopTable.select { ShopTable.id inList shopsIds }
        .map { Shop.from(it) }
        .associateBy { it.id }
    val products = ProductTable.select2(by = user) { ProductTable.id inList productsIds }
        .map { Product.from(it) }
        .associateBy { it.id }

    val productsOrdered = mutableListOf<Product>()
    val whenAddedOrdered = mutableListOf<Long>()
    val shopsOfProductsOrdered = mutableListOf<Shop>()
    for (row in productsAtShopsRows) {
        val product = products[row[ProductAtShopTable.productId]]
        val shop = shops[row[ProductAtShopTable.shopId]]
        if (product == null || shop == null) {
            continue
        }
        productsOrdered += product
        shopsOfProductsOrdered += shop
        whenAddedOrdered += row[ProductAtShopTable.creationTime]
    }

    LatestProductsAddedToShopsResponse(shopsOfProductsOrdered, productsOrdered, whenAddedOrdered)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LatestProductsAddedToShopsResponse(
    @JsonProperty("shops_ordered")
    val shopsOfProductsOrdered: List<Shop>,
    @JsonProperty("products_ordered")
    val productsOrdered: List<Product>,
    @JsonProperty("when_added_ordered")
    val whenAddedOrdered: List<Long>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
