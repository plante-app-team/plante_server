package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.CountModeratorTasksResponse
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/count_moderator_tasks/")
data class CountModeratorTasksParams(
    val includeTypes: List<String>? = null,
    val excludeTypes: List<String>? = null)

fun countModeratorTasks(params: CountModeratorTasksParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    return transaction {
        var includeTypesStrs = params.includeTypes ?: ModeratorTaskType.values().map { it.typeName }
        val excludeTypesStrs = params.excludeTypes ?: emptyList()
        includeTypesStrs = includeTypesStrs.filter { !excludeTypesStrs.contains(it) }
        val includeTypes = includeTypesStrs
            .mapNotNull { ModeratorTaskType.fromTaskTypeName(it) }
            .map { it.persistentCode }

        val conn = TransactionManager.current().connection
        val statement = conn.prepareStatement(
            "SELECT ${ModeratorTaskTable.lang.name}, COUNT(${ModeratorTaskTable.lang.name}) " +
            "FROM ${ModeratorTaskTable.tableName} " +
            "WHERE " +
                    "${ModeratorTaskTable.resolutionTime.name} IS NULL AND " +
                    "${ModeratorTaskTable.taskType.name} IN (${includeTypes.joinToString(", ")}) " +
            "GROUP BY ${ModeratorTaskTable.lang.name};", false)

        val selected = statement.executeQuery()
        val langsCounts = mutableMapOf<String, Long>()
        while (selected.next()) {
            val count = selected.getLong("count")
            val lang = selected.getString(ModeratorTaskTable.lang.name)
            if (lang == null) {
                continue
            }
            langsCounts[lang] = count
        }

        val totalCount = ModeratorTaskTable.select {
            (ModeratorTaskTable.resolutionTime eq null) and
                    (ModeratorTaskTable.taskType inList includeTypes)
        }.count()

        CountModeratorTasksResponse(totalCount, langsCounts)
    }
}
