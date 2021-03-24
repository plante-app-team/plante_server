package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

object UserTable : Table("user") {
    val id = uuid("id")
    val loginGeneration = integer("login_generation")
    val name = text("name")
    val googleId = text("google_id").nullable()
    override val primaryKey = PrimaryKey(id)
}
