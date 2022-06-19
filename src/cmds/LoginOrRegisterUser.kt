package vegancheckteam.plante_server.cmds

import io.ktor.client.HttpClient
import io.ktor.locations.Location
import java.util.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.auth.AppleAuthorizer
import vegancheckteam.plante_server.auth.GoogleAuthorizer
import vegancheckteam.plante_server.auth.GoogleIdOrServerError
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.auth.authOrServerError
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserDataResponse
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/login_or_register_user/")
data class LoginOrRegisterUserParams(
    val googleIdToken: String? = null,
    val appleAuthorizationCode: String? = null,
    val deviceId: String,
    val userName: String? = null)

suspend fun loginOrRegisterUser(params: LoginOrRegisterUserParams, client: HttpClient, testing: Boolean): Any {
    var googleId: String? = null
    var appleId: String? = null
    if (params.googleIdToken != null) {
        val idOrError = GoogleAuthorizer.authOrServerError(params.googleIdToken, testing)
        googleId = when (idOrError) {
            is GoogleIdOrServerError.Error -> return idOrError.error
            is GoogleIdOrServerError.Ok -> idOrError.googleId
        }
    } else if (params.appleAuthorizationCode != null) {
        val idOrError = AppleAuthorizer.auth(
            testing,
            params.appleAuthorizationCode,
            Config.instance.iOSBackendPrivateKeyFilePath,
            client)
        appleId = when (idOrError) {
            is AppleAuthorizer.AuthResult.Ok -> idOrError.appleId
        }
    } else {
        throw IllegalArgumentException("Both Google ID and Apple ID are nulls")
    }

    val existingUser = transaction {
        UserTable.select {
            ((UserTable.googleId eq googleId) and (UserTable.googleId neq null)) or
                    ((UserTable.appleId eq appleId) and (UserTable.appleId neq null))
        }.firstOrNull()
    }
    return if (existingUser == null) {
        registerUserImpl(params, googleId = googleId, appleId = appleId)
    } else {
        loginUserImpl(User.from(existingUser), params)
    }
}

private fun registerUserImpl(
    params: LoginOrRegisterUserParams,
    googleId: String? = null,
    appleId: String? = null,
): UserDataResponse {
    if (googleId == null && appleId == null) {
        throw IllegalArgumentException("Both Google ID and Apple ID are nulls");
    }
    val user = User(
        id = UUID.randomUUID(),
        loginGeneration = 1,
        googleId = googleId,
        appleId = appleId,
    )
    val jwtToken = JwtController.makeToken(user, params.deviceId)

    val alwaysModeratorName = Config.instance.alwaysModeratorName
    val userGroup = if (alwaysModeratorName != null && params.userName == alwaysModeratorName) {
        UserRightsGroup.ADMINISTRATOR
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
            it[UserTable.appleId] = user.appleId
            it[userRightsGroup] = userGroup.persistentCode
        }[UserTable.id]
    }

    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}

fun loginUserImpl(user: User, params: LoginOrRegisterUserParams): UserDataResponse {
    val jwtToken = JwtController.makeToken(user, params.deviceId)
    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}
