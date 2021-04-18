package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.ModeratorTask
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import java.time.ZonedDateTime
import java.util.*

@Location("/assign_moderator_task/")
data class AssignModeratorTaskParams(
    val taskId: Int? = null,
    val assignee: String? = null,
    val testingNow: Long? = null)

fun assignModeratorTask(params: AssignModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val now = if (params.testingNow != null && testing) {
        params.testingNow
    } else {
        ZonedDateTime.now().toEpochSecond()
    }

    val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60

    return transaction {
        val taskId = if (params.taskId != null) {
            val row = ModeratorTaskTable.select {
                ModeratorTaskTable.id eq params.taskId
            }.firstOrNull()
            if (row == null) {
                return@transaction GenericResponse.failure("task_not_found", "Task for id ${params.taskId} not found")
            }
            params.taskId
        } else {
            val task = ModeratorTaskTable.select {
                (ModeratorTaskTable.resolutionTime eq null) and
                        ((ModeratorTaskTable.assignTime eq null) or
                        (ModeratorTaskTable.assignTime less oldestAcceptable))
            }.orderBy(ModeratorTaskTable.creationTime)
                .map { ModeratorTask.from(it) }
                .sortedBy { it.taskType.priority }
                .firstOrNull()

            if (task == null) {
                return@transaction GenericResponse.failure("no_unresolved_moderator_tasks")
            } else {
                task.id
            }
        }

        val assignee = if (params.assignee != null) {
            val assignee = UserTable.select {
                UserTable.id eq UUID.fromString(params.assignee)
            }.map { User.from(it) }.first()
            if (assignee.userRightsGroup != UserRightsGroup.MODERATOR) {
                return@transaction GenericResponse.failure("assignee_not_moderator")
            }
            assignee.id
        } else {
            user.id
        }
        ModeratorTaskTable.update({ ModeratorTaskTable.id eq taskId }) {
            it[ModeratorTaskTable.assignee] = assignee
            it[assignTime] = now
        }
        GenericResponse.success()
    }
}
