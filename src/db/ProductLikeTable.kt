package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductLikeTable : Table("product_like_table") {
    val id = integer("id").autoIncrement()
    val userId = uuid("user_id").references(UserTable.id).index()
    val barcode = text("barcode").index()
    val time = long("time").index()
    override val primaryKey = PrimaryKey(id)
    init {
        index(true, userId, barcode)
    }
}
