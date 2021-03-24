package vegancheckteam.untitled_vegan_app_server.routes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/update_user_data/")
data class UpdateUserDataParams(val newName: String?)

fun updateUserData(params: UpdateUserDataParams, user: User): Any {
    transaction {
        UserTable.update({ UserTable.id eq user.id }) { row ->
            params.newName?.let { row[name] = it }
        }
    }
    return HttpResponse.success("ok")
}
