package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/ban_me/")
data class BanMeParams(val unused: Int? = null)

fun banMe(user: User): Any {
    transaction {
        UserTable.update({ UserTable.id eq user.id }) {
            it[banned] = true
        }
    }
    return HttpResponse.success()
}
