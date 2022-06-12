package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/delete_news_piece/")
data class DeleteNewsPieceParams(val newsPieceId: Int)

fun deleteNewsPiece(params: DeleteNewsPieceParams, requester: User): Any {
    if (requester.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    transaction {
        NewsPieceProductAtShopTable.deleteWhere { NewsPieceProductAtShopTable.newsPieceId eq params.newsPieceId }
        NewsPieceTable.deleteWhere { NewsPieceTable.id eq params.newsPieceId }
    }
    return GenericResponse.success()
}
