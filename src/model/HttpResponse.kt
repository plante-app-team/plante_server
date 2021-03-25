package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import vegancheckteam.untitled_vegan_app_server.base.readOrNull

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpResponse(
    @JsonProperty("error")
    val error: String?,
    @JsonProperty("error_description")
    val errorDescription: String?,
    @JsonProperty("result")
    val result: String?) {

    companion object {
        private val mapper = ObjectMapper()
        fun fromString(str: String): HttpResponse? = mapper.readOrNull(str)
        fun success() = HttpResponse(null, null, "ok")
        fun failure(error: String, errorDescription: String? = null) = HttpResponse(error, errorDescription, null)
    }

    override fun toString(): String = mapper.writeValueAsString(this)
}
