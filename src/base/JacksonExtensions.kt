package vegancheckteam.untitled_vegan_app_server.base

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper

inline fun <reified T> ObjectMapper.readOrNull(content: String): T? {
    return try {
        readValue(content, T::class.java)
    } catch (e: JsonParseException) {
        // TODO(https://trello.com/c/XgGFE05M/): log warning
        null
    } catch (e: JsonMappingException) {
        // TODO(https://trello.com/c/XgGFE05M/): log warning
        null
    }
}
