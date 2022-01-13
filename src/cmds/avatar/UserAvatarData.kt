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

const val USER_AVATAR_DATA = "/user_avatar_data"
const val USER_AVATAR_DATA_USER_ID_PARAM = "user_id"

suspend fun userAvatarData(call: ApplicationCall, user: User) {
    val userId = call.parameters[USER_AVATAR_DATA_USER_ID_PARAM]
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

    if (!targetUser.hasAvatar) {
        val msg = "User does not have avatar in DB: $userId"
        Log.i("UserAvatarData", msg)
        call.respond(HttpStatusCode.NotFound, msg)
        return
    }

    val avatar = S3.getData(userAvatarPathS3(user))
    if (avatar == null) {
        transaction {
            UserTable.update({ UserTable.id eq user.id }) {
                it[hasAvatar] = false
            }
        }
        val msg = "S3 does not have user avatar: ${userAvatarPathS3(user)}"
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
