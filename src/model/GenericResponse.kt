package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.untitled_vegan_app_server.GlobalStorage.jsonMapper
import vegancheckteam.untitled_vegan_app_server.base.readOrNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenericResponse(
    @JsonProperty("error")
    val error: String?,
    @JsonProperty("error_description")
    val errorDescription: String?,
    @JsonProperty("result")
    val result: String?) {

    companion object {
        fun fromString(str: String): GenericResponse? = jsonMapper.readOrNull(str)
        fun success() = GenericResponse(null, null, "ok")
        fun failure(error: String, errorDescription: String? = null) = GenericResponse(error, errorDescription, null)
    }

    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
