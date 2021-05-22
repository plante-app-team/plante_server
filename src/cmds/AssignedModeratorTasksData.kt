package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.model.ModeratorTasksDataResponse
import java.util.*
import vegancheckteam.plante_server.base.now

const val ASSIGNATION_TIME_LIMIT_MINUTES = 5L

@Location("/assigned_moderator_tasks_data/")
data class AssignedModeratorTasksDataParams(
    val assignee: String? = null,
    val includeResolved: Boolean = false,
    val testingNow: Long? = null)

fun assignedModeratorTasksData(params: AssignedModeratorTasksDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val now = now(params.testingNow, testing)

    return transaction {
        val assignee = if (params.assignee != null) {
            UUID.fromString(params.assignee)
        } else {
            user.id
        }

        val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60

        val mainConstraint = (ModeratorTaskTable.assignee eq assignee) and
                (ModeratorTaskTable.assignTime greater oldestAcceptable)
        val query = if (params.includeResolved) {
            ModeratorTaskTable.select {
                mainConstraint
            }
        } else {
            ModeratorTaskTable.select {
                mainConstraint and
                        (ModeratorTaskTable.resolutionTime eq null)
            }
        }
        val tasks = query
            .orderBy(ModeratorTaskTable.creationTime)
            .map { ModeratorTask.from(it) }
        ModeratorTasksDataResponse(tasks)
    }
}

