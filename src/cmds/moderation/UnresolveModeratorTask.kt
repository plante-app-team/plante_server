package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/unresolve_moderator_task/")
data class UnresolveModeratorTaskParams(
    val taskId: Int)

fun unresolveModeratorTask(params: UnresolveModeratorTaskParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    val updated = transaction {
        ModeratorTaskTable.update( { ModeratorTaskTable.id eq params.taskId } ) {
            it[resolutionTime] = null
            it[resolver] = null
            it[resolverAction] = null
        }
    }
    if (updated > 0) {
        return GenericResponse.success()
    } else {
        return GenericResponse.failure("task_not_found", "Task for id ${params.taskId} not found")
    }
}
