package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ShopTable : Table("shop") {
    val id = integer("id").autoIncrement()
    val osmId = text("osm_id").uniqueIndex()
    val creationTime = long("creation_time").index()
    val createdNewOsmNode = bool("created_new_osm_node").default(false)
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).index()
    val productsCount = integer("products_count").default(0)
    override val primaryKey = PrimaryKey(id)
}
