package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table
import vegancheckteam.plante_server.model.UserRightsGroup

object UserTable : Table("user") {
    val id = uuid("id")
    val banned = bool("banned").default(false)
    val googleId = text("google_id").nullable()
    val appleId = text("apple_id").nullable()
    val loginGeneration = integer("login_generation")
    val creationTime = long("creation_time").index()
    val name = text("name")
    val gender = short("gender").nullable()
    val birthday = text("birthday").nullable()
    val eatsMilk = bool("eats_milk").nullable() // TODO: remove
    val eatsEggs = bool("eats_eggs").nullable() // TODO: remove
    val eatsHoney = bool("eats_honey").nullable() // TODO: remove
    val langsPrioritized = text("langs_prioritized").nullable()
    val userRightsGroup = short("user_rights_group").default(UserRightsGroup.NORMAL.persistentCode).index()
    val hasAvatar = bool("has_avatar").default(false)
    override val primaryKey = PrimaryKey(id)
}

fun UserTable.joinLangs(langs: List<String>) = langs.joinToString(separator = ",")
fun UserTable.splitLangs(langsStr: String) = langsStr.split(',')
