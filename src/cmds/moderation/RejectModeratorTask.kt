package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/reject_moderator_task/")
data class RejectModeratorTaskParams(
    val taskId: Int,
    val testingNow: Long? = null)

fun rejectModeratorTask(params: RejectModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    return transaction {
        val row = ModeratorTaskTable.select {
            (ModeratorTaskTable.id eq params.taskId) and
                    (ModeratorTaskTable.assignee eq user.id)
        }.firstOrNull()
        if (row == null) {
            return@transaction GenericResponse.success()
        }

        val task = ModeratorTask.from(row).addRejectedAssignee(user.id)
        ModeratorTaskTable.update {
            it[assignee] = null
            it[assignTime] = null
            it[rejectedAssigneesList] = task.joinRejectedAssignees()
        }
        GenericResponse.success()
    }
}
