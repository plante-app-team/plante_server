package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.auth.GoogleAuthorizer
import vegancheckteam.untitled_vegan_app_server.auth.GoogleIdOrServerError
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.auth.authOrServerError
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.responses.model.UserDataResponse

@Location("/login_user/")
data class LoginParams(val googleIdToken: String, val deviceId: String)

fun loginUser(params: LoginParams, testing: Boolean): Any {
    val idOrError = GoogleAuthorizer.authOrServerError(params.googleIdToken, testing)
    val googleId = when (idOrError) {
        is GoogleIdOrServerError.Error -> return idOrError.error
        is GoogleIdOrServerError.Ok -> idOrError.googleId
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
