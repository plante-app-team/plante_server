package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage.jsonMapper
import vegancheckteam.plante_server.db.ProductTable

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Product(
    @JsonProperty("server_id")
    val id: Int,
    @JsonProperty("barcode")
    val barcode: String,
    @JsonProperty("vegetarian_status")
    val vegetarianStatus: VegStatus?,
    @JsonProperty("vegan_status")
    val veganStatus: VegStatus?,
    @JsonProperty("vegetarian_status_source")
    val vegetarianStatusSource: VegStatusSource?,
    @JsonProperty("vegan_status_source")
    val veganStatusSource: VegStatusSource?) {

    companion object {
        fun from(tableRow: ResultRow) = Product(
            id = tableRow[ProductTable.id],
            barcode = tableRow[ProductTable.barcode],
            vegetarianStatus = vegStatusFrom(tableRow[ProductTable.vegetarianStatus]),
            veganStatus = vegStatusFrom(tableRow[ProductTable.veganStatus]),
            vegetarianStatusSource = vegStatusSourceFrom(tableRow[ProductTable.vegetarianStatusSource]),
            veganStatusSource = vegStatusSourceFrom(tableRow[ProductTable.veganStatusSource]))
        private fun vegStatusFrom(code: Short?) = code?.let { VegStatus.fromPersistentCode(it) }
        private fun vegStatusSourceFrom(code: Short?) = code?.let { VegStatusSource.fromPersistentCode(it) }
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
