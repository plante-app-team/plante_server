package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.model.ModeratorTasksDataResponse
import vegancheckteam.plante_server.base.now

@Location("/all_moderator_tasks_data/")
data class AllModeratorTasksDataParams(
    val includeResolved: Boolean = false,
    val page: Long = 0,
    val testingNow: Long? = null)

fun allModeratorTasksData(params: AllModeratorTasksDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    val now = now(params.testingNow, testing)

    return transaction {
        deleteResolvedTasks(now)

        val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60

        val query = if (params.includeResolved) {
            ModeratorTaskTable.selectAll()
        } else {
            ModeratorTaskTable.select {
                ModeratorTaskTable.resolutionTime eq null
            }
        }
        val tasks = query
            .orderBy(ModeratorTaskTable.creationTime)
            .limit(10, 10 * params.page)
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
