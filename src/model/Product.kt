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
    @JsonProperty("vegan_status")
    val veganStatus: VegStatus?,
    @JsonProperty("vegan_status_source")
    val veganStatusSource: VegStatusSource?,
    @JsonProperty("moderator_vegan_choice_reason")
    val moderatorVeganChoiceReason: Short?,
    @JsonProperty("moderator_vegan_sources_text")
    val moderatorVeganSourcesText: String?) {

    companion object {
        fun from(tableRow: ResultRow) = Product(
            id = tableRow[ProductTable.id],
            barcode = tableRow[ProductTable.barcode],
            veganStatus = vegStatusFrom(tableRow[ProductTable.veganStatus]),
            veganStatusSource = vegStatusSourceFrom(tableRow[ProductTable.veganStatusSource]),
            moderatorVeganChoiceReason = tableRow[ProductTable.moderatorVeganChoiceReason],
            moderatorVeganSourcesText = tableRow[ProductTable.moderatorVeganSourcesText])
        private fun vegStatusFrom(code: Short?) = code?.let { VegStatus.fromPersistentCode(it) }
        private fun vegStatusSourceFrom(code: Short?) = code?.let { VegStatusSource.fromPersistentCode(it) }
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
