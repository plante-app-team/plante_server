package vegancheckteam.untitled_vegan_app_server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import vegancheckteam.untitled_vegan_app_server.GlobalStorage
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse

object GoogleAuthorizer {
    private val jsonFactory = GsonFactory()

    sealed class AuthResult {
        data class Ok(val googleId: String) : AuthResult()
        object EmailNotVerified : AuthResult()
        object Failure : AuthResult()
    }

    /**
     * @return User's Google ID or null if not authed.
     */
    fun auth(idTokenString: String, httpTransport: HttpTransport): AuthResult {
        val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
            .setAudience(listOf("84481772151-aisj7p71ovque8tbsi8ribpc5iv7bpjd.apps.googleusercontent.com"))
            .build()
        val idToken = verifier.verify(idTokenString)

        val payload = idToken?.payload
        if (payload == null || payload.subject == null) {
            return AuthResult.Failure
        }
        if (!payload.emailVerified) {
            return AuthResult.EmailNotVerified
        }
        return AuthResult.Ok(payload.subject)
    }
}

sealed class GoogleIdOrServerError {
    data class Ok(val googleId: String) : GoogleIdOrServerError()
    data class Error(val error: GenericResponse) : GoogleIdOrServerError()
}

fun GoogleAuthorizer.authOrServerError(googleIdToken: String, testing: Boolean): GoogleIdOrServerError {
    val googleAuthResult = if (!testing) {
        auth(googleIdToken, GlobalStorage.httpTransport)
    } else {
        when (googleIdToken) {
            "GOOGLE_AUTH_FAIL_FOR_TESTING" -> GoogleAuthorizer.AuthResult.Failure
            "GOOGLE_AUTH_EMAIL_NOT_VERIFIED" -> GoogleAuthorizer.AuthResult.EmailNotVerified
            else -> GoogleAuthorizer.AuthResult.Ok(googleIdToken)
        }
    }

    return when (googleAuthResult) {
        is GoogleAuthorizer.AuthResult.Ok -> {
            GoogleIdOrServerError.Ok(googleAuthResult.googleId)
        }
        is GoogleAuthorizer.AuthResult.EmailNotVerified -> {
            // TODO(https://trello.com/c/XgGFE05M/): log warning
            GoogleIdOrServerError.Error(GenericResponse.failure("google_email_not_verified"))
        }
        is GoogleAuthorizer.AuthResult.Failure -> {
            // TODO(https://trello.com/c/XgGFE05M/): log warning
            GoogleIdOrServerError.Error(GenericResponse.failure("google_auth_failed"))
        }
    }
}
