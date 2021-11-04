package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTask
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.model.ModeratorTasksDataResponse
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.cmds.moderation.sanitizeModerationTasks
import vegancheckteam.plante_server.model.ModeratorTaskType

@Location("/all_moderator_tasks_data/")
data class AllModeratorTasksDataParams(
    val includeResolved: Boolean = false,
    val page: Long = 0,
    val pageSize: Int = 10,
    val lang: String? = null,
    val onlyWithNoLang: Boolean = false,
    val testingNow: Long? = null)

fun allModeratorTasksData(params: AllModeratorTasksDataParams, user: User, testing: Boolean): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    if (params.onlyWithNoLang && params.lang != null) {
        return GenericResponse.failure("invalid_params", "should not provide both 'lang' and 'onlyWithNoLang'")
    }

    val now = now(params.testingNow, testing)

    return transaction {
        sanitizeModerationTasks(now)

        val resolvedOp = if (!params.includeResolved) {
            ModeratorTaskTable.resolutionTime eq null
        } else {
            Op.TRUE
        }
        val langOp = if (params.onlyWithNoLang) {
            ModeratorTaskTable.lang eq null
        } else if (params.lang != null) {
            ModeratorTaskTable.lang eq params.lang
        } else {
            Op.TRUE
        }
        val mainOp = resolvedOp and langOp

        // Code below solves a complex problem.
        // The problem can be described in the next way:
        // - The caller wants to use pagination.
        // - The caller wants to receive the high-priority tasks first.
        // - Priority of tasks is not stored in DB, instead it's a part of
        //   the [ModeratorTaskType] enum, because priority can (and do) change from
        //   time to time.
        // - Since priorities are not stored in DB, we cannot simply do a SELECT with an
        //   ORDER BY - we must distinctly select tasks of all types, starting with
        //   the high-priority types.
        // - Tasks requests with pagination quite often create complex situations for code -
        //   if the caller wants 10 task at the page #2, we have to line up all existing tasks
        //   in such a way that the high-priority task will be first, middle-priority middle, etc.
        //   Only then we will be able to collect the 10 tasks requested by the caller.
        //   But we cannot do the "line up" directly - it would require us to select ALL existing
        //   tasks from the DB, which makes pagination pointless.
        // - To solve all of the above, the code below first calculates the needed offset
        //   to return requested tasks, only then it starts to collect them.
        // See also [ModerationRequests_Pagination_Test].

        val tasksTypes = ModeratorTaskType.values().sortedBy { it.priority }
        val countsOfTasksTypes = mutableMapOf<ModeratorTaskType, Long>()
        for (taskType in tasksTypes) {
            val where = mainOp and (ModeratorTaskTable.taskType eq taskType.persistentCode)
            countsOfTasksTypes[taskType] = ModeratorTaskTable.select(where).count()
        }

        val requestedTasksStartOffset = params.pageSize * params.page
        var currentTaskTypeEndOffset = 0L
        val result = mutableListOf<ModeratorTask>()
        for (taskType in tasksTypes) {
            val currentTypeTasksCount = countsOfTasksTypes[taskType]!!
            val currentTaskTypeStartOffset = currentTaskTypeEndOffset
            currentTaskTypeEndOffset += currentTypeTasksCount
            if (currentTaskTypeEndOffset < requestedTasksStartOffset) {
                continue
            }
            val taskSpecificStartOffset = if (result.isEmpty()) {
                requestedTasksStartOffset - currentTaskTypeStartOffset
            } else {
                // We already collected tasks of some type,
                // all next tasks should be collected from their starting offset
                0L
            }

            val where = mainOp and (ModeratorTaskTable.taskType eq taskType.persistentCode)
            val tasks = ModeratorTaskTable.select(where)
                .orderBy(ModeratorTaskTable.creationTime)
                .limit(params.pageSize, taskSpecificStartOffset)
                .map { ModeratorTask.from(it) }
            result.addAll(tasks)
            if (result.size >= params.pageSize) {
                break
            }
        }
        ModeratorTasksDataResponse(result.take(params.pageSize))
    }
}
