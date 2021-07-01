package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductTable : Table("product") {
    val id = integer("id").autoIncrement()
    val barcode = text("barcode").uniqueIndex()
    val vegetarianStatus = short("vegetarian_status")
    val veganStatus = short("vegan_status")
    val vegetarianStatusSource = short("vegetarian_status_source")
    val veganStatusSource = short("vegan_status_source")
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).nullable().index()
    override val primaryKey = PrimaryKey(id)
}
