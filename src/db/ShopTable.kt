package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ShopTable : Table("shop") {
    val id = integer("id").autoIncrement()
    /**
     * PLEASE NOTE: this ID is not the same thing as the ID in Open Street Map.
     * is a combination of multiple OSM elements fields to make
     * the ID of an [osmUID] unique even among multiple OSM elements types.
     * Hence, "UID" - Unique IDentifier.
     */
    // NOTE: column name is deprecated -
    // should be renamed to "osm_uid", but it's not easy to do with Exposed
    val osmUID = text("osm_id").uniqueIndex()
    val creationTime = long("creation_time").index()
    val createdNewOsmNode = bool("created_new_osm_node").default(false)
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).index()
    val productsCount = integer("products_count").default(0)
    override val primaryKey = PrimaryKey(id)
}
