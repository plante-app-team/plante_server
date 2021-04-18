package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.ModeratorTask
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import vegancheckteam.untitled_vegan_app_server.responses.model.ModeratorTasksDataResponse
import java.time.ZonedDateTime

@Location("/all_moderator_tasks_data/")
data class AllModeratorTasksDataParams(
    val testingNow: Long? = null)

fun allModeratorTasksData(params: AllModeratorTasksDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val now = if (params.testingNow != null && testing) {
        params.testingNow
    } else {
        ZonedDateTime.now().toEpochSecond()
    }

    return transaction {
        val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60

        val tasks = ModeratorTaskTable.selectAll()
            .orderBy(ModeratorTaskTable.creationTime)
            .map { ModeratorTask.from(it) }
            .map {
                when {
                    it.assignTime == null -> it
                    it.assignTime < oldestAcceptable -> it.copy(assignTime = null, assignee = null)
                    else -> it
                }
            }
        ModeratorTasksDataResponse(tasks)
    }
}
