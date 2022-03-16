package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object NewsPieceTable : Table("news_piece_table") {
    val id = integer("id").autoIncrement()
    val lat = double("lat").index()
    val lon = double("lon").index()
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).index()
    val creationTime = long("creation_time").index()
    val type = short("type").index()
    override val primaryKey = PrimaryKey(id)
}
