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
    @JsonProperty("moderator_vegan_choice_reasons")
    val moderatorVeganChoiceReasons: String?,
    @JsonProperty("moderator_vegan_sources_text")
    val moderatorVeganSourcesText: String?) {

    companion object {
        private const val MODERATOR_CHOICE_REASON_SEPARATOR = ","
        fun from(tableRow: ResultRow): Product {
            val moderatorVeganChoiceReasons = tableRow[ProductTable.moderatorVeganChoiceReasons]
            val moderatorVeganChoiceReason: Short?
            if (moderatorVeganChoiceReasons.isNullOrBlank()) {
                moderatorVeganChoiceReason = null
            } else {
                moderatorVeganChoiceReason = moderatorVeganChoiceReasons
                    .split(MODERATOR_CHOICE_REASON_SEPARATOR)
                    .firstOrNull()
                    ?.toShortOrNull()
            }
            return Product(
                id = tableRow[ProductTable.id],
                barcode = tableRow[ProductTable.barcode],
                veganStatus = vegStatusFrom(tableRow[ProductTable.veganStatus]),
                veganStatusSource = vegStatusSourceFrom(tableRow[ProductTable.veganStatusSource]),
                moderatorVeganChoiceReason = moderatorVeganChoiceReason,
                moderatorVeganChoiceReasons = tableRow[ProductTable.moderatorVeganChoiceReasons],
                moderatorVeganSourcesText = tableRow[ProductTable.moderatorVeganSourcesText])
        }
        private fun vegStatusFrom(code: Short?) = code?.let { VegStatus.fromPersistentCode(it) }
        private fun vegStatusSourceFrom(code: Short?) = code?.let { VegStatusSource.fromPersistentCode(it) }
        fun moderatorChoiceReasonsToStr(reasons: List<Int>?): String? {
            if (reasons == null || reasons.isEmpty()) {
                return null
            }
            return reasons.joinToString(MODERATOR_CHOICE_REASON_SEPARATOR)
        }
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
