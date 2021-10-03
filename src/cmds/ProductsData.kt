package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.model.Product

const val PRODUCTS_DATA_PARAMS_PAGE_SIZE = 24

@Location("/products_data/")
data class ProductsDataParams(
    val barcodes: List<String>,
    val page: Long)

fun productsData(params: ProductsDataParams) = transaction {
    val products = ProductTable.select {
        ProductTable.barcode inList params.barcodes
    }.orderBy(ProductTable.barcode)
     .limit(PRODUCTS_DATA_PARAMS_PAGE_SIZE + 1, PRODUCTS_DATA_PARAMS_PAGE_SIZE * params.page)
     .map { Product.from(it) }
    return@transaction ProductsDataResponse(
        // We've requested PRODUCTS_DATA_PARAMS_PAGE_SIZE+1 products to figure out whether
        // the requested page is the last one. Now we'll remove the extra product so that
        // our clients wouldn't rely on the 1 extra product.
        products = products.take(PRODUCTS_DATA_PARAMS_PAGE_SIZE),
        // If number of products is less or equal to PRODUCTS_DATA_PARAMS_PAGE_SIZE
        // than it's the last page, because we've requested PRODUCTS_DATA_PARAMS_PAGE_SIZE+1
        // products.
        lastPage = products.size <= PRODUCTS_DATA_PARAMS_PAGE_SIZE)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductsDataResponse(
    @JsonProperty("products")
    val products: List<Product>,
    @JsonProperty("last_page")
    val lastPage: Boolean) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
