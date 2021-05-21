package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@Location("/ban_me/")
data class BanMeParams(val unused: Int? = null)

fun banMe(user: User): Any {
    transaction {
        UserTable.update({ UserTable.id eq user.id }) {
            it[banned] = true
        }
    }
    return GenericResponse.success()
}
