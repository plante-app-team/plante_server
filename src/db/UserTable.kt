package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table
import vegancheckteam.untitled_vegan_app_server.model.UserRightsGroup

object UserTable : Table("user") {
    val id = uuid("id")
    val banned = bool("banned").default(false)
    val googleId = text("google_id").nullable()
    val loginGeneration = integer("login_generation")
    val creationTime = long("creation_time")
    val name = text("name")
    val gender = short("gender").nullable()
    val birthday = text("birthday").nullable()
    val eatsMilk = bool("eats_milk").nullable()
    val eatsEggs = bool("eats_eggs").nullable()
    val eatsHoney = bool("eats_honey").nullable()
    val userRightsGroup = short("user_rights_group").default(UserRightsGroup.NORMAL.persistentCode)
    override val primaryKey = PrimaryKey(id)
}
