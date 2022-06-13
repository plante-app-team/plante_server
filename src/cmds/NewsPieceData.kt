package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.news.NewsPiece

@Location("/news_piece_data/")
data class NewsPieceDataParams(
    val newsPieceId: Int,
)

fun newsPieceData(params: NewsPieceDataParams): Any {
    val pieces = transaction {
        NewsPiece.selectFromDB(
            where = NewsPieceTable.id eq params.newsPieceId,
            pageSize = 1,
            pageNumber = 0,
        )
    }

    if (pieces.isEmpty()) {
        return GenericResponse.failure(
            "news_piece_not_found",
            "No news piece with ID: ${params.newsPieceId}",
        )
    }

    return pieces.first()
}
