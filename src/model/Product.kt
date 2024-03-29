package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import vegancheckteam.plante_server.GlobalStorage.jsonMapper

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
    val moderatorVeganSourcesText: String?,
    @JsonProperty("liked_by_me")
    val likedByMe: Boolean,
    @JsonProperty("likes_count")
    val likesCount: Long) {

    companion object {
        const val MODERATOR_CHOICE_REASON_SEPARATOR = ","

        fun moderatorChoiceReasonsToStr(reasons: List<Int>?): String? {
            if (reasons.isNullOrEmpty()) {
                return null
            }
            return reasons.joinToString(MODERATOR_CHOICE_REASON_SEPARATOR)
        }
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
