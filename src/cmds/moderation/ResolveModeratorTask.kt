package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.cmds.moderation.sanitizeModerationTasks

@Location("/resolve_moderator_task/")
data class ResolveModeratorTaskParams(
    val taskId: Int,
    val performedAction: String,
    val testingNow: Long? = null)

fun resolveModeratorTask(params: ResolveModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    val now = now(params.testingNow, testing)

    val updated = transaction {
        sanitizeModerationTasks(now)
        ModeratorTaskTable.update( { ModeratorTaskTable.id eq params.taskId } ) {
            it[resolutionTime] = now
            it[resolver] = user.id
            it[resolverAction] = params.performedAction
        }
    }
    if (updated > 0) {
        return GenericResponse.success()
    } else {
        return GenericResponse.failure("task_not_found", "Task for id ${params.taskId} not found")
    }
}
