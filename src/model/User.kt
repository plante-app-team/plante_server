package vegancheckteam.untitled_vegan_app_server.model

import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.untitled_vegan_app_server.db.UserTable

data class User(
    val id: UUID,
    val loginGeneration: Int,
    val name: String,
    val googleId: String?) {

    companion object {
        fun from(tableRow: ResultRow) = User(
                id = tableRow[UserTable.id],
                loginGeneration = tableRow[UserTable.loginGeneration],
                name = tableRow[UserTable.name],
                googleId = tableRow[UserTable.googleId])
    }
}
