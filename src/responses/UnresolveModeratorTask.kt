package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup
import java.time.ZonedDateTime

@Location("/unresolve_moderator_task/")
data class UnresolveModeratorTaskParams(
    val taskId: Int)

fun unresolveModeratorTask(params: UnresolveModeratorTaskParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup != UserRightsGroup.MODERATOR) {
        return GenericResponse.failure("denied")
    }

    val updated = transaction {
        ModeratorTaskTable.update( { ModeratorTaskTable.id eq params.taskId } ) {
            it[resolutionTime] = null
        }
    }
    if (updated > 0) {
        return GenericResponse.success()
    } else {
        return GenericResponse.failure("task_not_found", "Task for id ${params.taskId} not found")
    }
}
