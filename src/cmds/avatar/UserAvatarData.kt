package cmds.avatar

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondOutputStream
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.aws.S3
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.User

private const val USER_AVATAR_DATA_USER_ID_PARAM = "user_id"
private const val USER_AVATAR_DATA_AVATAR_ID_PARAM = "avatar_id"
const val USER_AVATAR_DATA =
    "/user_avatar_data/{$USER_AVATAR_DATA_USER_ID_PARAM}/{$USER_AVATAR_DATA_AVATAR_ID_PARAM}"

suspend fun userAvatarData(call: ApplicationCall, requester: User) {
    val userId = call.parameters[USER_AVATAR_DATA_USER_ID_PARAM]
    val avatarId = call.parameters[USER_AVATAR_DATA_AVATAR_ID_PARAM]
    val targetUser = transaction {
        UserTable
            .select(UserTable.id eq UUID.fromString(userId))
            .map { User.from(it) }
            .firstOrNull()
    }
    if (targetUser == null) {
        val msg = "User not found: $userId"
        Log.i("UserAvatarData", msg)
        call.respond(HttpStatusCode.NotFound, msg)
        return
    }
    if (avatarId == null) {
        val msg = "Avatar ID must be provided"
        Log.i("UserAvatarData", msg)
        call.respond(HttpStatusCode.NotFound, msg)
        return
    }

    val userAvatarPath = userAvatarPathS3(targetUser.id.toString(), avatarId)
    val avatar = S3.getData(userAvatarPath)
    if (avatar == null) {
        if (targetUser.avatarId.toString() == avatarId) {
            // Avatar was not found in S3, and it's same avatar as
            // the one stored in the DB.
            transaction {
                UserTable.update({ UserTable.id eq targetUser.id }) {
                    it[UserTable.avatarId] = null
                }
            }
        }
        val msg = "S3 does not have user avatar: $userAvatarPath"
        Log.w("UserAvatarData", msg)
        call.respond(HttpStatusCode.NotFound, msg)
        return
    }

    call.respondOutputStream(
            status = HttpStatusCode.OK,
            contentType = ContentType.Image.JPEG) {
        val out = this
        withContext(Dispatchers.IO) {
            avatar.transferTo(out)
            avatar.close()
            close()
        }
    }
}
