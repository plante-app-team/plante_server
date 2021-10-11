package vegancheckteam.plante_server.base

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper

inline fun <reified T> ObjectMapper.readOrNull(content: String): T? {
    return try {
        readValue(content, T::class.java)
    } catch (e: JsonParseException) {
        Log.w("ObjectMapper.readOrNull", "JsonParseException", e)
        null
    } catch (e: JsonMappingException) {
        Log.w("ObjectMapper.readOrNull", "JsonMappingException", e)
        null
    }
}
