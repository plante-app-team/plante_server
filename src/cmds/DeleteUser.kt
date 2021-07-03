package vegancheckteam.plante_server.cmds

import io.ktor.locations.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import java.util.*

@Location("/delete_user/")
data class DeleteUserParams(val userId: String)

fun deleteUser(params: DeleteUserParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.EVERYTHING_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    val updated = transaction {
        UserTable.update({ UserTable.id eq UUID.fromString(params.userId) }) { row ->
            row[googleId] = null
            row[name] = ""
            row[birthday] = null
            row[loginGeneration] = user.loginGeneration + 1 // Sign out
        }
    }
    return if (updated > 0) {
        GenericResponse.success()
    } else {
        GenericResponse.failure("user_not_found", "No user with ID: ${params.userId}")
    }
}
