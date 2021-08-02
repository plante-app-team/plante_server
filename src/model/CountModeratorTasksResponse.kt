package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.GlobalStorage.jsonMapper

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CountModeratorTasksResponse(
    @JsonProperty("total_count")
    val totalCount: Long,
    @JsonProperty("langs_counts")
    val langsCounts: Map<String, Long>) {
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
