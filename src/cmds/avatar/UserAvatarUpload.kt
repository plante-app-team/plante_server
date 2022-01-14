package cmds.avatar

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveStream
import io.ktor.response.respondText
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.aws.DataTooLargeException
import vegancheckteam.plante_server.aws.S3
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

const val USER_AVATAR_MAX_SIZE = 512 * 1024
const val USER_AVATAR_UPLOAD = "/user_avatar_upload/"

suspend fun userAvatarUpload(call: ApplicationCall, user: User) {
    Log.i("UserAvatarUpload", "Uploading avatar for ${user.id}")
    try {
        S3.putData(userAvatarPathS3(user), call.receiveStream(), maxBytes = USER_AVATAR_MAX_SIZE)
    } catch (e: DataTooLargeException) {
        call.respondText(status = HttpStatusCode.PayloadTooLarge, text = "")
        return
    }
    transaction {
        UserTable.update({ UserTable.id eq user.id }) {
            it[hasAvatar] = true
        }
    }
    call.respondText(GenericResponse.success().toString())
}

fun userAvatarPathS3(user: User) = "avatar/${user.id}"
