package vegancheckteam.untitled_vegan_app_server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory

object GoogleAuthorizer {
    private val jsonFactory = GsonFactory()

    /**
     * @return User's Google ID or null if not authed.
     */
    fun auth(idTokenString: String, httpTransport: HttpTransport): String? {
        val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
            .setAudience(listOf("84481772151-aisj7p71ovque8tbsi8ribpc5iv7bpjd.apps.googleusercontent.com"))
            .build()
        val idToken = verifier.verify(idTokenString)
        return idToken?.payload?.subject
    }
}