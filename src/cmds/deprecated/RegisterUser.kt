package cmds.deprecated

import io.ktor.client.HttpClient
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
import vegancheckteam.plante_server.auth.AppleAuthorizer
import vegancheckteam.plante_server.auth.AppleIdOrServerError
import vegancheckteam.plante_server.base.now

@Location("/register_user/")
data class RegisterParams(
    val googleIdToken: String? = null,
    val appleAuthorizationCode: String? = null,
    val deviceId: String,
    val userName: String? = null)

suspend fun registerUser(params: RegisterParams, client: HttpClient, testing: Boolean): Any {
    if (params.googleIdToken != null) {
        return googleAuth(testing, params, params.googleIdToken)
    } else if (params.appleAuthorizationCode != null) {
        return appleAuth(testing, params, params.appleAuthorizationCode, client)
    }
    throw IllegalArgumentException("Both Google ID and Apple ID are nulls")
}

private fun googleAuth(testing: Boolean, params: RegisterParams, googleIdToken: String): Any {
    val idOrError = GoogleAuthorizer.authOrServerError(googleIdToken, testing)
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
    return registerUserImpl(googleId, null, params)
}

private fun registerUserImpl(
    googleId: String?,
    appleId: String?,
    params: RegisterParams
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

suspend fun appleAuth(testing: Boolean,
                      params: RegisterParams,
                      appleAuthorizationCode: String,
                      client: HttpClient): Any {
    val idOrError = AppleAuthorizer.auth(
        testing,
        appleAuthorizationCode,
        Config.instance.iOSBackendPrivateKeyFilePath,
        client)
    val appleId = when (idOrError) {
        is AppleAuthorizer.AuthResult.Ok -> idOrError.appleId
    }

    val existingUser = transaction {
        UserTable.select {
            UserTable.appleId eq appleId
        }.firstOrNull()
    }
    if (existingUser != null) {
        return GenericResponse.failure("already_registered")
    }
    return registerUserImpl(null, appleId, params)
}
