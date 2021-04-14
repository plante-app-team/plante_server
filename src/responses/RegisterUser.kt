package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.insert
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
import java.time.ZonedDateTime
import java.util.*

@Location("/register_user/")
data class RegisterParams(val googleIdToken: String, val deviceId: String)

fun registerUser(params: RegisterParams, testing: Boolean): Any {
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
    if (existingUser != null) {
        return GenericResponse.failure("already_registered")
    }

    val user = User(
        id = UUID.randomUUID(),
        loginGeneration = 1,
        googleId = googleId)
    val jwtToken = JwtController.makeToken(user, params.deviceId)

    transaction {
        UserTable.insert {
            it[id] = user.id
            it[loginGeneration] = user.loginGeneration
            it[creationTime] = ZonedDateTime.now().toEpochSecond()
            it[name] = user.name
            it[UserTable.googleId] = user.googleId
        }[UserTable.id]
    }

    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}
