package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.model.OsmUID

object NewsPieceProductAtShopTable : Table("news_piece_product_at_shop") {
    val id = integer("id").autoIncrement()
    val newsPieceId = integer("news_piece_id").references(NewsPieceTable.id).index()
    val barcode = text("barcode").index()
    val shopUID = text("shop_uid").index()
    override val primaryKey = PrimaryKey(id)
}

fun NewsPieceProductAtShopTable.selectIDsWhere(where: () -> Op<Boolean>): List<Int> {
    return NewsPieceProductAtShopTable.select {
        where.invoke()
    }.map { it[newsPieceId] }
}

fun NewsPieceProductAtShopTable.deepDeleteNewsForBarcode(barcode: String) {
    deepDeleteNewsForEither(barcode = barcode, shop = null)
}

fun NewsPieceProductAtShopTable.deepDeleteNewsForShop(shop: OsmUID) {
    deepDeleteNewsForEither(barcode = null, shop = shop)
}

private fun deepDeleteNewsForEither(barcode: String?, shop: OsmUID?) {
    if (barcode != null) {
        NewsPieceTable.deepDeleteNewsWhere {
            NewsPieceTable.id inList NewsPieceProductAtShopTable
                .selectIDsWhere { NewsPieceProductAtShopTable.barcode eq barcode }
        }
    } else if (shop != null) {
        NewsPieceTable.deepDeleteNewsWhere {
            NewsPieceTable.id inList NewsPieceProductAtShopTable
                .selectIDsWhere { NewsPieceProductAtShopTable.shopUID eq shop.asStr }
        }
    } else {
        throw IllegalArgumentException()
    }
}
