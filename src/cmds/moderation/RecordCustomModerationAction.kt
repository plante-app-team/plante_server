package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/record_custom_moderation_action/")
data class RecordCustomModerationActionParams(
    val performedAction: String,
    val barcode: String? = null,
    val osmUID: String? = null,
    val testingNow: Long? = null)

fun recordCustomModerationAction(params: RecordCustomModerationActionParams, user: User, testing: Boolean) = transaction {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return@transaction GenericResponse.failure("denied")
    }
    // Creating an already-resolved task
    ModeratorTaskTable.insert {
        it[taskSourceUserId] = user.id
        it[resolver] = user.id
        it[resolverAction] = params.performedAction
        it[creationTime] = now(params.testingNow, testing)
        it[resolutionTime] = now(params.testingNow, testing)
        it[productBarcode] = params.barcode
        it[osmUID] = params.osmUID
        it[taskType] = ModeratorTaskType.CUSTOM_MODERATION_ACTION.persistentCode
    }
    GenericResponse.success()
}
