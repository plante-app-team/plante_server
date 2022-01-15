package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import java.util.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.UserContributionTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserContribution
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/user_contributions_data/")
data class UserContributionsDataParams(
    val contributionsTypes: List<Int>,
    val limit: Int,
    val userId: String? = null)

fun userContributionsData(params: UserContributionsDataParams, user: User): Any {
    if (params.userId != null
        && user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    val userId = params.userId?.let { UUID.fromString(it) } ?: user.id

    val contributions = transaction {
        val contributionTypes = params.contributionsTypes.map { it.toShort() }
        val query = (UserContributionTable.userId eq userId) and
                (UserContributionTable.type inList contributionTypes)
        UserContributionTable
            .select(query)
            .orderBy(UserContributionTable.time, order = SortOrder.DESC)
            .limit(params.limit)
            .map { UserContribution.from(it) }
    }
    return UserContributionsDataResponse(contributions)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserContributionsDataResponse(
    @JsonProperty("result")
    val result: List<UserContribution>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
