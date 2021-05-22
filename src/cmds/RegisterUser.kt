package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.auth.GoogleAuthorizer
import vegancheckteam.plante_server.auth.GoogleIdOrServerError
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.auth.authOrServerError
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.model.UserDataResponse
import java.util.*
import vegancheckteam.plante_server.base.now

@Location("/register_user/")
data class RegisterParams(
    val googleIdToken: String,
    val deviceId: String,
    val userName: String? = null)

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

    val alwaysModeratorName = Config.instance.alwaysModeratorName
    val userGroup = if (alwaysModeratorName != null && params.userName == alwaysModeratorName) {
        UserRightsGroup.MODERATOR
    } else {
        UserRightsGroup.NORMAL
    }

    transaction {
        UserTable.insert {
            it[id] = user.id
            it[loginGeneration] = user.loginGeneration
            it[creationTime] = now()
            it[name] = user.name
            it[UserTable.googleId] = user.googleId
            it[userRightsGroup] = userGroup.persistentCode
        }[UserTable.id]
    }

    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}
