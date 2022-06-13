package cmds.moderation

import io.ktor.locations.Location
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/user_ban/")
data class BanUserParams(
    val userId: String,
    val unban: Boolean = false,
)

fun banUser(params: BanUserParams, requester: User): Any {
    if (requester.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
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

    val updated = transaction {
        val banned = !params.unban
        UserTable.update({ UserTable.id eq targetUser.id }) {
            it[UserTable.banned] = banned
        }
    }

    return if (updated > 0) {
        GenericResponse.success()
    } else {
        GenericResponse.failure("internal_error", "Could not delete: ${params.userId}")
    }
}
