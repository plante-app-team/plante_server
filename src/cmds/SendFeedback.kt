package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.base.now

const val FEEDBACK_MAX_LENGTH = 10000

@Location("/send_feedback/")
data class SendFeedbackParams(
    val text: String,
    val testingNow: Long? = null)

fun sendFeedback(params: SendFeedbackParams, user: User, testing: Boolean): Any {
    if (FEEDBACK_MAX_LENGTH < params.text.length) {
        return GenericResponse.failure("feedback_too_long")
    }

    val now = now(testingNow = params.testingNow, testing)
    transaction {
        ModeratorTaskTable.insert {
            it[taskType] = ModeratorTaskType.USER_FEEDBACK.persistentCode
            it[taskSourceUserId] = user.id
            it[textFromUser] = params.text
            it[creationTime] = now
        }
    }
    return GenericResponse.success()
}
