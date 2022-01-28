package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.GlobalStorage.jsonMapper
import vegancheckteam.plante_server.base.readOrNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenericResponse(
    @JsonProperty("error")
    val error: String?,
    @JsonProperty("error_description")
    val errorDescription: String?,
    @JsonProperty(RESULT_FIELD_NAME)
    val result: String?) {

    companion object {
        const val RESULT_FIELD_NAME = "result"
        const val RESULT_FIELD_OK_VAL = "ok"
        fun fromString(str: String): GenericResponse? = jsonMapper.readOrNull(str)
        fun success(result: String = RESULT_FIELD_OK_VAL) = GenericResponse(null, null, result)
        fun failure(error: String, errorDescription: String? = null) = GenericResponse(error, errorDescription, null)
    }

    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
