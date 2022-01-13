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
    val gender: Gender? = null,
    val birthday: String? = null,
    val eatsMilk: Boolean? = null,
    val eatsEggs: Boolean? = null,
    val eatsHoney: Boolean? = null,
    val langsPrioritizedStr: String? = null,
    val userRightsGroup: UserRightsGroup = UserRightsGroup.NORMAL,
    val hasAvatar: Boolean = false) {

    companion object {
        fun from(tableRow: ResultRow) = User(
                id = tableRow[UserTable.id],
                banned = tableRow[UserTable.banned],
                loginGeneration = tableRow[UserTable.loginGeneration],
                name = tableRow[UserTable.name],
                googleId = tableRow[UserTable.googleId],
                appleId = tableRow[UserTable.appleId],
                gender = tableRow[UserTable.gender]?.let { Gender.fromPersistentCode(it) },
                birthday = tableRow[UserTable.birthday],
                eatsMilk = tableRow[UserTable.eatsMilk],
                eatsEggs = tableRow[UserTable.eatsEggs],
                eatsHoney = tableRow[UserTable.eatsHoney],
                langsPrioritizedStr = tableRow[UserTable.langsPrioritized],
                userRightsGroup = extractUserRightsGroup(tableRow[UserTable.userRightsGroup]),
                hasAvatar = tableRow[UserTable.hasAvatar])
        private fun extractUserRightsGroup(code: Short) =
            UserRightsGroup.fromPersistentCode(code) ?: UserRightsGroup.NORMAL
    }
}
