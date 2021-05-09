package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ShopTable : Table("shop") {
    val id = integer("id").autoIncrement()
    val osmId = text("osm_id").uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}
