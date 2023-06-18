package cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
        NewsPieceTable.update( { NewsPieceTable.id eq params.newsPieceId } ) {
            it[deleted] = true
        }
    }
    return GenericResponse.success()
}
