package vegancheckteam.plante_server.cmds.avatar

import cmds.avatar.userAvatarPathS3
import io.ktor.locations.Location
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.aws.S3
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/user_avatar_delete/")
data class UserAvatarDeleteParams(
    val userId: String? = null)

suspend fun userAvatarDelete(params: UserAvatarDeleteParams, user: User): Any {
    if (params.userId != null
        && user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    val userId = params.userId?.let { UUID.fromString(it) } ?: user.id

    val targetUser = transaction {
        UserTable
            .select(UserTable.id eq userId)
            .map { User.from(it) }
            .firstOrNull()
    }
    if (targetUser == null) {
        return GenericResponse.failure("user_not_found", "No user with ID: ${params.userId}")
    }
    if (targetUser.avatarId != null) {
        S3.deleteData(userAvatarPathS3(targetUser.id.toString(), targetUser.avatarId.toString()))
        transaction {
            UserTable.update({ UserTable.id eq user.id }) {
                it[avatarId] = null
            }
        }
    }
    return GenericResponse.success()
}
