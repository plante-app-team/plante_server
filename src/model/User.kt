package vegancheckteam.untitled_vegan_app_server.model

import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.untitled_vegan_app_server.db.UserTable

enum class Gender(val genderName: String) {
    MALE("male"),
    FEMALE("female");
    companion object {
        fun fromStringName(str: String): Gender? {
            return when (str.toLowerCase()) {
                MALE.genderName -> MALE
                FEMALE.genderName -> FEMALE
                else -> null
            }
        }
    }
}

data class User(
    val id: UUID,
    val loginGeneration: Int,
    val googleId: String?,
    val banned: Boolean = false,
    val name: String = "",
    val gender: Gender? = null,
    val birthday: String? = null,
    val eatsMilk: Boolean? = null,
    val eatsEggs: Boolean? = null,
    val eatsHoney: Boolean? = null) {

    companion object {
        fun from(tableRow: ResultRow) = User(
                id = tableRow[UserTable.id],
                banned = tableRow[UserTable.banned],
                loginGeneration = tableRow[UserTable.loginGeneration],
                name = tableRow[UserTable.name],
                googleId = tableRow[UserTable.googleId],
                gender = tableRow[UserTable.gender]?.let { Gender.fromStringName(it) },
                birthday = tableRow[UserTable.birthday],
                eatsMilk = tableRow[UserTable.eatsMilk],
                eatsEggs = tableRow[UserTable.eatsEggs],
                eatsHoney = tableRow[UserTable.eatsHoney])
    }
}
