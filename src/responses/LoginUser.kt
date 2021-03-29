package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.GlobalStorage
import vegancheckteam.untitled_vegan_app_server.auth.GoogleAuthorizer
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.responses.model.UserDataResponse

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
        return GenericResponse.failure("google_auth_failed")
    }

    val existingUser = transaction {
        UserTable.select {
            UserTable.googleId eq googleId
        }.firstOrNull()
    }
    if (existingUser == null) {
        return GenericResponse.failure("not_registered")
    }

    val user = User.from(existingUser)
    val jwtToken = JwtController.makeToken(user, params.deviceId)

    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}
