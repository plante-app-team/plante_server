package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/moderator_task_data/")
data class ModeratorTaskDataParams(
    val taskId: Int,
    val testingNow: Long? = null)

fun moderatorTaskData(params: ModeratorTaskDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    return transaction {
        val row = ModeratorTaskTable.select {
            (ModeratorTaskTable.id eq params.taskId) and
                    (ModeratorTaskTable.resolutionTime eq null)
        }.firstOrNull()
        if (row == null) {
            return@transaction GenericResponse.failure(
                "task_not_found",
                "Task for id ${params.taskId} not found")
        }
        ModeratorTask.from(row)
    }
}
