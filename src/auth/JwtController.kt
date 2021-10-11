package vegancheckteam.plante_server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.auth.jwt.JWTCredential
import java.util.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.User

// WARNING: beware of JWT changes - any change can lead to all tokens invalidation
object JwtController {
    private const val claimUserId = "user_id"
    private const val claimLoginGeneration = "login_generation"
    private const val claimDeviceId = "device_id"

    private const val issuer = "vegancheckteam"
    private val secret = lazy { Config.instance.jwtSecret }
    private val algorithm = lazy{ Algorithm.HMAC256(secret.value) }

    val verifier = lazy { JWT.require(algorithm.value).withIssuer(issuer).build() }

    fun makeToken(user: User, deviceId: String): String {
        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withClaim(claimUserId, user.id.toString())
            .withClaim(claimLoginGeneration, user.loginGeneration)
            .withClaim(claimDeviceId, deviceId)
            .sign(Algorithm.HMAC256(secret.value))
    }

    fun principalFromCredential(credential: JWTCredential): UserPrincipal? {
        val userIdStr = credential.payload.getClaim(claimUserId) ?: return null
        val userId = try {
            UUID.fromString(userIdStr.asString())
        } catch (e: IllegalArgumentException) {
            return null
        }
        val loginGeneration = credential
            .payload
            .getClaim(claimLoginGeneration)
            ?.asInt()
            ?: return null

        val userRow = transaction {
            UserTable.select {
                UserTable.id eq userId
            }.firstOrNull()
        }
        if (userRow == null) {
            Log.w("JwtController", "user not found with $userIdStr")
            return null
        }

        val user = User.from(userRow)
        if (loginGeneration != user.loginGeneration) {
            Log.w("JwtController",
                "user found but generation differs: $userIdStr, $loginGeneration vs ${user.loginGeneration}")
            return null
        }

        return UserPrincipal(user)
    }
}
