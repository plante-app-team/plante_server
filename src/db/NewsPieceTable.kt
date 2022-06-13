package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.model.news.NewsPieceType

object NewsPieceTable : Table("news_piece_table") {
    val id = integer("id").autoIncrement()
    val lat = double("lat").index()
    val lon = double("lon").index()
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).index()
    val creationTime = long("creation_time").index()
    val type = short("type").index()
    override val primaryKey = PrimaryKey(id)
}

fun NewsPieceTable.deepDeleteNewsWhere(where: () -> Op<Boolean>) {
    val whereOp = where.invoke()
    val news = NewsPieceTable
        .select(whereOp)
    for (newsType in NewsPieceType.values()) {
        val typedNews = news.filter { it[type] == newsType.persistentCode }
        newsType.deleteWhereParentsAre(typedNews.map { it[id] })
    }
    NewsPieceTable.deleteWhere { whereOp }
}

private fun NewsPieceType.deleteWhereParentsAre(ids: List<Int>) {
    when (this) {
        NewsPieceType.PRODUCT_AT_SHOP -> NewsPieceProductAtShopTable.deleteWhere {
            NewsPieceProductAtShopTable.newsPieceId inList ids
        }
    }
}
