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
     .limit(PRODUCTS_DATA_PARAMS_PAGE_SIZE, PRODUCTS_DATA_PARAMS_PAGE_SIZE * params.page)
     .map { Product.from(it) }
    return@transaction ProductsDataResponse(products)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductsDataResponse(
    @JsonProperty("products")
    val products: List<Product>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
