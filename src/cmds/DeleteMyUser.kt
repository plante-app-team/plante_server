package cmds

import cmds.moderation.deleteUserImpl
import io.ktor.client.HttpClient
import io.ktor.locations.Location
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.auth.AppleAuthorizer
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@Location("/delete_my_user/")
data class DeleteMyUserParams(
    val userId: String,
    val googleIdToken: String? = null,
    val appleAuthorizationCode: String? = null,
)

suspend fun deleteMyUser(params: DeleteMyUserParams, requester: User, client: HttpClient, testing: Boolean): Any {
    if (requester.appleId != null && params.appleAuthorizationCode == null) {
        return GenericResponse.failure("invalid_params", "Apple users need to provide Apple auth code")
    }

    if (params.appleAuthorizationCode != null) {
        val authResult = AppleAuthorizer.auth(
            testing,
            params.appleAuthorizationCode,
            Config.instance.iOSBackendPrivateKeyFilePath,
            client,
        )
        val authOk = when (authResult) {
            is AppleAuthorizer.AuthResult.Ok -> authResult
        }

        val revokeResult = AppleAuthorizer.revokeTokens(
            authOk,
            testing,
            params.appleAuthorizationCode,
            Config.instance.iOSBackendPrivateKeyFilePath,
            client,
        )
        if (revokeResult !is AppleAuthorizer.RevokeResult.Ok) {
            return GenericResponse.failure("apple_revoke_error", "Could not revoke Apple tokens")
        }
    }

    return deleteUserImpl(requester.id.toString())
}
