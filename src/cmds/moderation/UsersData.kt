package vegancheckteam.plante_server.cmds.moderation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import java.util.UUID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserDataResponse
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/users_data/")
data class UsersDataParams(
    val ids: List<String>)

fun usersData(params: UsersDataParams, user: User) = transaction {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return@transaction GenericResponse.failure("denied")
    }

    val ids = params.ids.map { UUID.fromString(it) }
    val users = UserTable.select {
        UserTable.id inList ids
    }.map { UserDataResponse.from(User.from(it)) }

    UsersDataResponse(users)
}


@JsonInclude(JsonInclude.Include.NON_NULL)
private data class UsersDataResponse(
    @JsonProperty("result")
    val result: List<UserDataResponse>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
