package vegancheckteam.plante_server.model.news

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.UserTable

data class NewsPiece(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("lat")
    val lat: Double,
    @JsonProperty("lon")
    val lon: Double,
    @JsonProperty("creator_user_id")
    val creatorUserId: UUID,
    @JsonProperty("creator_user_name")
    val creatorUserName: String,
    @JsonProperty("creation_time")
    val creationTime: Long,
    @JsonProperty("type")
    val type: Short,
    @JsonProperty("data")
    val data: Map<*, *>,
) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        // NOTE: in a perfect world this function would be in some other place,
        // but it makes the code simpler when the client can just do "NewsPiece.selectFromDB(..)"
        fun selectFromDB(where: Op<Boolean>, pageSize: Int, pageNumber: Int): List<NewsPiece> {
            val pieces = NewsPieceTable.join(
                    UserTable,
                    joinType = JoinType.LEFT,
                    onColumn = NewsPieceTable.creatorUserId,
                    otherColumn = UserTable.id)
                .select { where and (NewsPieceTable.deleted eq false) }
                .orderBy(NewsPieceTable.creationTime, order = SortOrder.DESC)
                .limit(n = pageSize + 1, offset = (pageNumber * pageSize).toLong())
                .map { from(it) }

            val result = mutableListOf<NewsPiece>()
            for (newsType in NewsPieceType.values()) {
                val piecesWithType = pieces.filter { it.type == newsType.persistentCode }
                val piecesMap = piecesWithType.associateBy { it.id }
                val data = newsType.select(piecesWithType.map { it.id })
                for (dataEntry in data) {
                    val piece = piecesMap[dataEntry.newsPieceId]
                    if (piece == null) {
                        Log.e("NewsPiece.selectFromDB", "Can't get news piece even though it must exist")
                        continue
                    }
                    result.add(piece.copy(data = dataEntry.toData()))
                }
            }
            result.sortByDescending { it.creationTime }
            return result
        }

        private fun from(tableRow: ResultRow): NewsPiece {
            return NewsPiece(
                id = tableRow[NewsPieceTable.id],
                lat = tableRow[NewsPieceTable.lat],
                lon = tableRow[NewsPieceTable.lon],
                creatorUserId = tableRow[NewsPieceTable.creatorUserId],
                creatorUserName = tableRow[UserTable.name],
                creationTime = tableRow[NewsPieceTable.creationTime],
                type = tableRow[NewsPieceTable.type],
                data = emptyMap<Any, Any>(),
            )
        }
    }
}

private fun NewsPieceType.select(ids: List<Int>): List<NewsPieceDataBase> {
    return when (this) {
        NewsPieceType.PRODUCT_AT_SHOP -> NewsPieceProductAtShopTable.select(
            NewsPieceProductAtShopTable.newsPieceId inList ids)
            .map { NewsPieceProductAtShop.from(it) }
    }
}
