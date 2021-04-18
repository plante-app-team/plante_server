package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.ModeratorTask
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import vegancheckteam.untitled_vegan_app_server.responses.model.ModeratorTasksDataResponse
import java.time.ZonedDateTime
import java.util.*

const val ASSIGNATION_TIME_LIMIT_MINUTES = 5L

@Location("/assigned_moderator_tasks_data/")
data class AssignedModeratorTasksDataParams(
    val assignee: String? = null,
    val testingNow: Long? = null)

fun assignedModeratorTasksData(params: AssignedModeratorTasksDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val now = if (params.testingNow != null && testing) {
        params.testingNow
    } else {
        ZonedDateTime.now().toEpochSecond()
    }

    return transaction {
        val assignee = if (params.assignee != null) {
            UUID.fromString(params.assignee)
        } else {
            user.id
        }

        val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60

        val tasks = ModeratorTaskTable.select {
            (ModeratorTaskTable.assignee eq assignee) and
                    (ModeratorTaskTable.assignTime greater oldestAcceptable)
        }.orderBy(ModeratorTaskTable.creationTime).map { ModeratorTask.from(it) }
        ModeratorTasksDataResponse(tasks)
    }
}

