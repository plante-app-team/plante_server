package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.GlobalStorage

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductsAtShopsResponse(
    @JsonProperty("results")
    val results: Map<String, ProductsAtShop>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductsAtShop(
    @JsonProperty("shop_osm_id")
    val shopOsmId: String,
    @JsonProperty("products")
    val products: MutableList<Product>,
    @JsonProperty("products_last_seen_utc")
    val productsLastSeen: MutableMap<String, Long>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
