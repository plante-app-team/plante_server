package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

object ProductTable : Table("product") {
    val id = integer("id").autoIncrement()
    val barcode = text("barcode").uniqueIndex()
    val vegetarianStatus = text("vegetarian_status")
    val veganStatus = text("vegan_status")
    val vegetarianStatusSource = text("vegetarian_status_source")
    val veganStatusSource = text("vegan_status_source")
    override val primaryKey = PrimaryKey(id)
}
