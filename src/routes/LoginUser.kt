package vegancheckteam.untitled_vegan_app_server.routes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.GlobalStorage
import vegancheckteam.untitled_vegan_app_server.auth.GoogleAuthorizer
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/login_user/")
data class LoginParams(val googleIdToken: String, val deviceId: String)

fun loginUser(params: LoginParams, testing: Boolean): Any {
    val googleId = if (!testing) {
        GoogleAuthorizer.auth(params.googleIdToken, GlobalStorage.httpTransport)
    } else {
        params.googleIdToken
    }

    if (googleId.isNullOrEmpty()) {
        // TODO(https://trello.com/c/XgGFE05M/): log warning
        return HttpResponse.failure("google_auth_failed")
    }

    val existingUser = transaction {
        UserTable.select {
            UserTable.googleId eq googleId
        }.firstOrNull()
    }
    if (existingUser == null) {
        return HttpResponse.failure("not_registered")
    }

    val user = User.from(existingUser)
    val jwtToken = JwtController.makeToken(user, params.deviceId)

    return LoginResponse(
        userId = existingUser[UserTable.id].toString(),
        name = existingUser[UserTable.name],
        client_token = jwtToken)
}

private data class LoginResponse(
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("client_token")
    val client_token: String,
    @JsonProperty("name")
    val name: String) {

    companion object {
        private val mapper = ObjectMapper()
    }
    override fun toString(): String = mapper.writeValueAsString(this)
}
