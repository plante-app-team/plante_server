package vegancheckteam.plante_server.cmds.moderation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/moderators_activities/")
data class ModeratorsActivitiesParams(
    val since: Long,
    val testingNow: Long? = null)

fun moderatorsActivities(params: ModeratorsActivitiesParams, user: User) = transaction {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return@transaction GenericResponse.failure("denied")
    }

    val tasks = ModeratorTaskTable.select {
        ModeratorTaskTable.resolutionTime greaterEq params.since
    }.orderBy(ModeratorTaskTable.resolutionTime, SortOrder.DESC)
        .map { ModeratorTask.from(it) }
    ModeratorsActivitiesResult(tasks)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class ModeratorsActivitiesResult(
    @JsonProperty("result")
    val result: List<ModeratorTask>,
) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
