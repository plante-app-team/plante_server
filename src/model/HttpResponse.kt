package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import vegancheckteam.untitled_vegan_app_server.base.readOrNull

data class HttpResponse(
    @JsonProperty("error")
    val error: String?,
    @JsonProperty("result")
    val result: String?) {

    companion object {
        private val mapper = ObjectMapper()
        fun fromString(str: String): HttpResponse? = mapper.readOrNull(str)
        fun success(result: String) = HttpResponse(null, result)
        fun failure(error: String) = HttpResponse(error, null)
    }

    override fun toString(): String = mapper.writeValueAsString(this)
}
