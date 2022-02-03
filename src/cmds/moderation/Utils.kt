package vegancheckteam.plante_server.cmds.moderation

import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ModeratorTaskTable

const val DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS = 365*10
const val ASSIGNATION_TIME_LIMIT_MINUTES = 5L

fun sanitizeModerationTasks(now: Long) {
    transaction {
        deleteResolvedTasks(now)
        unassignTimedOutTasks(now)
    }
}

private fun deleteResolvedTasks(now: Long) {
    val earliestAllowedTasksExistence = now - DELETE_RESOLVED_MODERATOR_TASKS_AFTER_DAYS * 24 * 60 * 60
    ModeratorTaskTable.deleteWhere {
        ModeratorTaskTable.resolutionTime less earliestAllowedTasksExistence
    }
}

private fun unassignTimedOutTasks(now: Long) {
    val oldestAcceptable = now - ASSIGNATION_TIME_LIMIT_MINUTES * 60
    ModeratorTaskTable.update({ ModeratorTaskTable.assignTime less oldestAcceptable }) {
        it[assignee] = null
        it[assignTime] = null
    }
}
