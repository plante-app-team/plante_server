package vegancheckteam.plante_server.model

import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.db.UserTable

data class User(
    val id: UUID,
    val loginGeneration: Int,
    val googleId: String? = null,
    val appleId: String? = null,
    val banned: Boolean = false,
    val name: String = "",
    val selfDescription: String? = null,
    val gender: Gender? = null,
    val birthday: String? = null,
    val langsPrioritizedStr: String? = null,
    val userRightsGroup: UserRightsGroup = UserRightsGroup.NORMAL,
    val avatarId: UUID? = null) {

    companion object {
        fun from(tableRow: ResultRow) = User(
                id = tableRow[UserTable.id],
                banned = tableRow[UserTable.banned],
                loginGeneration = tableRow[UserTable.loginGeneration],
                name = tableRow[UserTable.name],
                selfDescription = tableRow[UserTable.selfDescription],
                googleId = tableRow[UserTable.googleId],
                appleId = tableRow[UserTable.appleId],
                gender = tableRow[UserTable.gender]?.let { Gender.fromPersistentCode(it) },
                birthday = tableRow[UserTable.birthday],
                langsPrioritizedStr = tableRow[UserTable.langsPrioritized],
                userRightsGroup = extractUserRightsGroup(tableRow[UserTable.userRightsGroup]),
                avatarId = tableRow[UserTable.avatarId])
        private fun extractUserRightsGroup(code: Short) =
            UserRightsGroup.fromPersistentCode(code) ?: UserRightsGroup.NORMAL
    }
}
