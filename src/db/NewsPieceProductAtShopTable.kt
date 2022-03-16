package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object NewsPieceProductAtShopTable : Table("news_piece_product_at_shop") {
    val id = integer("id").autoIncrement()
    val newsPieceId = integer("news_piece_id").references(NewsPieceTable.id).index()
    val barcode = text("barcode").index()
    val shopUID = text("shop_uid").index()
    override val primaryKey = PrimaryKey(id)
}
