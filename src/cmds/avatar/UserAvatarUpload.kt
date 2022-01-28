package cmds.avatar

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveStream
import io.ktor.response.respondText
import java.util.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.aws.DataTooLargeException
import vegancheckteam.plante_server.aws.S3
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.cmds.avatar.UserAvatarDeleteParams
import vegancheckteam.plante_server.cmds.avatar.userAvatarDelete
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

const val USER_AVATAR_MAX_SIZE = 512 * 1024
const val USER_AVATAR_UPLOAD = "/user_avatar_upload/"

suspend fun userAvatarUpload(call: ApplicationCall, user: User) {
    // First, let's delete the old one
    val deleteResult = userAvatarDelete(UserAvatarDeleteParams(), user)
    if (deleteResult is GenericResponse && deleteResult.error != null) {
        call.respondText(deleteResult.toString())
        return
    }

    Log.i("UserAvatarUpload", "Uploading avatar for ${user.id}")

    val avatarId = UUID.randomUUID()
    transaction {
        UserTable.update({ UserTable.id eq user.id }) {
            it[UserTable.avatarId] = avatarId
        }
    }
    val avatarPath = userAvatarPathS3(user.id.toString(), avatarId.toString())
    try {
        S3.putData(avatarPath, call.receiveStream(), maxBytes = USER_AVATAR_MAX_SIZE)
    } catch (e: DataTooLargeException) {
        transaction {
            UserTable.update({ UserTable.id eq user.id }) {
                it[UserTable.avatarId] = null
            }
        }
        call.respondText(status = HttpStatusCode.PayloadTooLarge, text = "")
        return
    }
    call.respondText(GenericResponse.success(avatarId.toString()).toString())
}

fun userAvatarPathS3(userId: String, avatarId: String) = "avatar/$userId/$avatarId"
