package vegancheckteam.plante_server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.security.interfaces.ECPrivateKey
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.now

object AppleAuthorizer {
    sealed class AuthResult {
        data class Ok(
            val appleId: String,
            val accessToken: String,
        ) : AuthResult()
    }

    sealed class RevokeResult {
        object Ok : RevokeResult()
        object Error : RevokeResult()
    }

    /**
     * @return User's Apple ID
     */
    suspend fun auth(testing: Boolean, idTokenString: String, privateKeyFilePath: String, client: HttpClient): AuthResult {
        if (testing) {
            return AuthResult.Ok(idTokenString, "")
        }

        Log.i("AppleAuthorizer", "auth, id token: $idTokenString")

        val jwt = getJWT(privateKeyFilePath)

        // https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
        val response = client.post<String>(urlString = "https://appleid.apple.com/auth/token?" +
                "client_id=vegancheckteam.plante&" +
                "code=$idTokenString&" +
                "client_secret=$jwt&" +
                "grant_type=authorization_code")
        Log.i("AppleAuthorizer", "auth, apple response: $response")

        @Suppress("BlockingMethodInNonBlockingContext")
        val json = GlobalStorage.jsonMapper.readValue(response, MutableMap::class.java)

        val responseJwt = JWT.decode(json["id_token"] as String)
        Log.i("AppleAuthorizer", "responseJwt: $responseJwt\n")
        val userId = responseJwt.subject
        val accessToken = json["access_token"] as String
        return AuthResult.Ok(userId, accessToken)
    }

    private fun getJWT(privateKeyFilePath: String): String {
        val privateKey = PemUtils.readPrivateKeyFromFile(privateKeyFilePath, "EC") as ECPrivateKey
        return JWT.create()
            .withHeader(mapOf("kid" to "2DLM7T56TB"))
            .withClaim("iss", "67WVPA59ZH")
            .withClaim("iat", now())
            .withClaim("exp", now() + 60 * 5)
            .withClaim("aud", "https://appleid.apple.com")
            .withClaim("sub", "vegancheckteam.plante")
            .sign(Algorithm.ECDSA256(null, privateKey))
    }

    // TODO: test real usage
    suspend fun revokeTokens(
            authResult: AuthResult.Ok,
            testing: Boolean,
            idTokenString: String,
            privateKeyFilePath: String,
            client: HttpClient,
    ): RevokeResult {
        if (testing) {
            return RevokeResult.Ok
        }

        Log.i("AppleAuthorizer", "revokeTokens, id token: $idTokenString")

        val jwt = getJWT(privateKeyFilePath)

        // https://developer.apple.com/documentation/sign_in_with_apple/revoke_tokens
        val response = client.post<HttpResponse>(urlString = "https://appleid.apple.com/auth/revoke?" +
                "client_id=vegancheckteam.plante&" +
                "client_secret=$jwt&" +
                "token=${authResult.accessToken}" +
                "token_type_hint=access_token")
        Log.i("AppleAuthorizer", "revokeTokens, apple response: $response")

        return if (response.status == HttpStatusCode.OK) {
            RevokeResult.Ok
        } else {
            RevokeResult.Error
        }
    }
}
