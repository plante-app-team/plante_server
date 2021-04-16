package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.untitled_vegan_app_server.GlobalStorage.jsonMapper
import vegancheckteam.untitled_vegan_app_server.db.ProductTable

data class Product(
    @JsonProperty("server_id")
    val id: Int,
    @JsonProperty("barcode")
    val barcode: String,
    @JsonProperty("vegetarian_status")
    val vegetarianStatus: VegStatus,
    @JsonProperty("vegan_status")
    val veganStatus: VegStatus,
    @JsonProperty("vegetarian_status_source")
    val vegetarianStatusSource: VegStatusSource,
    @JsonProperty("vegan_status_source")
    val veganStatusSource: VegStatusSource) {

    companion object {
        fun from(tableRow: ResultRow) = Product(
            id = tableRow[ProductTable.id],
            barcode = tableRow[ProductTable.barcode],
            vegetarianStatus = vegStatusFrom(tableRow[ProductTable.vegetarianStatus]),
            veganStatus = vegStatusFrom(tableRow[ProductTable.veganStatus]),
            vegetarianStatusSource = vegStatusSourceFrom(tableRow[ProductTable.vegetarianStatusSource]),
            veganStatusSource = vegStatusSourceFrom(tableRow[ProductTable.veganStatusSource]))
        private fun vegStatusFrom(code: Short) = VegStatus.fromPersistentCode(code) ?: VegStatus.UNKNOWN
        private fun vegStatusSourceFrom(code: Short) = VegStatusSource.fromPersistentCode(code) ?: VegStatusSource.UNKNOWN
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
