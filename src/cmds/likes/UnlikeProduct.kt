package vegancheckteam.plante_server.cmds.likes

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ProductLikeTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@Location("/unlike_product/")
data class UnlikeProductParams(
    val barcode: String,
)

fun unlikeProduct(params: UnlikeProductParams, user: User): Any {
    return transaction {
        ProductLikeTable.deleteWhere {
            (ProductLikeTable.barcode eq params.barcode) and
                    (ProductLikeTable.userId eq user.id)
        }
        GenericResponse.success()
    }
}
