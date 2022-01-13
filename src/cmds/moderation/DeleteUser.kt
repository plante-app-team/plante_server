package cmds.moderation

import cmds.avatar.userAvatarPathS3
import io.ktor.locations.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.aws.S3

@Location("/delete_user/")
data class DeleteUserParams(val userId: String)

suspend fun deleteUser(params: DeleteUserParams, requester: User): Any {
    if (requester.userRightsGroup.persistentCode < UserRightsGroup.EVERYTHING_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    val targetUser = transaction {
        UserTable
            .select(UserTable.id eq UUID.fromString(params.userId))
            .map { User.from(it) }
            .firstOrNull()
    }
    if (targetUser == null) {
        return GenericResponse.failure("user_not_found", "No user with ID: ${params.userId}")
    }

    if (targetUser.hasAvatar) {
        S3.deleteData(userAvatarPathS3(targetUser))
    }
    val updated = transaction {
        UserTable.update({ UserTable.id eq targetUser.id }) { row ->
            row[googleId] = null
            row[name] = ""
            row[birthday] = null
            row[loginGeneration] = targetUser.loginGeneration + 1 // Sign out
            row[hasAvatar] = false
        }
    }
    return if (updated > 0) {
        GenericResponse.success()
    } else {
        GenericResponse.failure("internal_error", "Could not delete: ${params.userId}")
    }
}
