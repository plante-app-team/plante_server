package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

object User : Table() {
    val id = uuid("id")
    val name = text("name")
    override val primaryKey = PrimaryKey(id)
}
