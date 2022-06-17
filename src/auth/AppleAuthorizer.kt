package vegancheckteam.plante_server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import java.security.interfaces.ECPrivateKey
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.model.GenericResponse

object AppleAuthorizer {
    sealed class AuthResult {
        data class Ok(val appleId: String) : AuthResult()
    }

    /**
     * @return User's Apple ID
     */
    suspend fun auth(testing: Boolean, idTokenString: String, privateKeyFilePath: String, client: HttpClient): AuthResult {
        if (testing) {
            return AuthResult.Ok(idTokenString)
        }

        Log.i("AppleAuthorizer", "auth, id token: $idTokenString")

        val privateKey = PemUtils.readPrivateKeyFromFile(privateKeyFilePath, "EC") as ECPrivateKey
        val jwt = JWT.create()
            .withHeader(mapOf("kid" to "2DLM7T56TB"))
            .withClaim("iss", "67WVPA59ZH")
            .withClaim("iat", now())
            .withClaim("exp", now() + 60 * 5)
            .withClaim("aud", "https://appleid.apple.com")
            .withClaim("sub", "vegancheckteam.plante")
            .sign(Algorithm.ECDSA256(null, privateKey))

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
        return AuthResult.Ok(userId)
    }
}
