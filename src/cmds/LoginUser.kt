package vegancheckteam.plante_server.cmds

import io.ktor.client.HttpClient
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.auth.AppleAuthorizer
import vegancheckteam.plante_server.auth.GoogleAuthorizer
import vegancheckteam.plante_server.auth.GoogleIdOrServerError
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.auth.authOrServerError
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserDataResponse

@Location("/login_user/")
data class LoginParams(
    val googleIdToken: String? = null,
    val appleAuthorizationCode: String? = null,
    val deviceId: String)

suspend fun loginUser(params: LoginParams, client: HttpClient, testing: Boolean): Any {
    if (params.googleIdToken == null && params.appleAuthorizationCode == null) {
        throw IllegalArgumentException("Both Google ID and Apple ID are nulls")
    }

    val existingUser = if (params.googleIdToken != null) {
        val idOrError = GoogleAuthorizer.authOrServerError(params.googleIdToken, testing)
        val googleId = when (idOrError) {
            is GoogleIdOrServerError.Error -> return idOrError.error
            is GoogleIdOrServerError.Ok -> idOrError.googleId
        }
        transaction {
            UserTable.select {
                UserTable.googleId eq googleId
            }.firstOrNull()
        }
    } else {
        val authResult = AppleAuthorizer.auth(
            testing,
            params.appleAuthorizationCode!!,
            Config.instance.iOSBackendPrivateKeyFilePath,
            client)
        val appleId = when (authResult) {
            is AppleAuthorizer.AuthResult.Ok -> authResult.appleId
        }
        transaction {
            UserTable.select {
                UserTable.appleId eq appleId
            }.firstOrNull()
        }
    }

    if (existingUser == null) {
        return GenericResponse.failure("not_registered")
    }
    val user = User.from(existingUser)
    val jwtToken = JwtController.makeToken(user, params.deviceId)
    return UserDataResponse.from(user).copy(clientToken = jwtToken)
}
