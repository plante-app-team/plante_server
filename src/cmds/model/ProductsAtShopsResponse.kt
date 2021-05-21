package vegancheckteam.plante_server.cmds.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.model.Product

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductsAtShopsResponse(
    @JsonProperty("results")
    val results: Map<String, List<Product>>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
