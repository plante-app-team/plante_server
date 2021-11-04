package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import java.util.*
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.cmds.moderation.sanitizeModerationTasks
import vegancheckteam.plante_server.db.splitLangs

@Location("/assign_moderator_task/")
data class AssignModeratorTaskParams(
    val taskId: Int? = null,
    val assignee: String? = null,
    val testingNow: Long? = null)

fun assignModeratorTask(params: AssignModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    val now = now(params.testingNow, testing)
    return transaction {
        sanitizeModerationTasks(now)

        val assignee = if (params.assignee != null) {
            val assignee = UserTable.select {
                UserTable.id eq UUID.fromString(params.assignee)
            }.map { User.from(it) }.first()
            if (assignee.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
                return@transaction GenericResponse.failure("assignee_not_moderator")
            }
            assignee
        } else {
            user
        }

        val assigneeId = assignee.id
        val assigneeLangs = assignee.langsPrioritizedStr?.let { UserTable.splitLangs(it) }

        val taskId = if (params.taskId != null) {
            val row = ModeratorTaskTable.select {
                ModeratorTaskTable.id eq params.taskId
            }.firstOrNull()
            if (row == null) {
                return@transaction GenericResponse.failure(
                    "task_not_found",
                    "Task for id ${params.taskId} not found")
            }
            params.taskId
        } else {
            val langsOp = if (assigneeLangs != null) {
                (ModeratorTaskTable.lang inList assigneeLangs) or (ModeratorTaskTable.lang eq null)
            } else {
                Op.TRUE
            }
            val mainOp = langsOp and (ModeratorTaskTable.resolutionTime eq null)
            val assignTimeOp = ModeratorTaskTable.assignTime eq null

            val task = ModeratorTaskTable.select {
                mainOp and assignTimeOp
            }.orderBy(ModeratorTaskTable.creationTime)
                .map { ModeratorTask.from(it) }
                .filter { !it.rejectedAssigneesList.contains(assigneeId) }
                .sortedBy { it.taskType.priority }
                .firstOrNull()

            if (task == null) {
                return@transaction GenericResponse.failure(
                    "no_unresolved_moderator_tasks")
            } else {
                task.id
            }
        }

        ModeratorTaskTable.update({ ModeratorTaskTable.id eq taskId }) {
            it[ModeratorTaskTable.assignee] = assigneeId
            it[assignTime] = now
        }
        GenericResponse.success()
    }
}
