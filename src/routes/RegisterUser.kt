package vegancheckteam.untitled_vegan_app_server.routes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.locations.Location
import java.util.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.GlobalStorage
import vegancheckteam.untitled_vegan_app_server.auth.GoogleAuthorizer
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/register_user/")
data class RegisterParams(val googleIdToken: String, val deviceId: String)

fun registerUser(params: RegisterParams, testing: Boolean): Any {
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
    if (existingUser != null) {
        return HttpResponse.failure("already_registered")
    }

    val user = User(
        id = UUID.randomUUID(),
        loginGeneration = 1,
        name = "",
        googleId = googleId)
    val jwtToken = JwtController.makeToken(user, params.deviceId)

    val userId = transaction {
        UserTable.insert {
            it[id] = user.id
            it[loginGeneration] = user.loginGeneration
            it[name] = user.name
            it[UserTable.googleId] = user.googleId
        }[UserTable.id]
    }

    return RegisterUserSuccess(userId.toString(), jwtToken)
}

private data class RegisterUserSuccess(
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("client_token")
    val client_token: String) {

    companion object {
        private val mapper = ObjectMapper()
    }
    override fun toString(): String = mapper.writeValueAsString(this)
}
