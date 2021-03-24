package vegancheckteam.untitled_vegan_app_server.routes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.locations.Location
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/user_data/")
data class UserDataParams(val unused: Int = 123)

fun userData(unused: UserDataParams, user: User): Any {
    return UserDataResponse(user.id.toString(), user.name)
}

private data class UserDataResponse(
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("name")
    val name: String) {

    companion object {
        private val mapper = ObjectMapper()
    }
    override fun toString(): String = mapper.writeValueAsString(this)
}
