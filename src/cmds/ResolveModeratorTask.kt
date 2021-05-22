package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.base.now

const val DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS = 7

@Location("/resolve_moderator_task/")
data class ResolveModeratorTaskParams(
    val taskId: Int,
    val testingNow: Long? = null)

fun resolveModeratorTask(params: ResolveModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val now = now(params.testingNow, testing)

    val updated = transaction {
        deleteResolvedTasks(now)
        ModeratorTaskTable.update( { ModeratorTaskTable.id eq params.taskId } ) {
            it[resolutionTime] = now
        }
    }
    if (updated > 0) {
        return GenericResponse.success()
    } else {
        return GenericResponse.failure("task_not_found", "Task for id ${params.taskId} not found")
    }
}

fun deleteResolvedTasks(now: Long) {
    val earliestAllowedTasksExistence = now - DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS * 24 * 60 * 60
    ModeratorTaskTable.deleteWhere {
        ModeratorTaskTable.resolutionTime less earliestAllowedTasksExistence
    }
}
