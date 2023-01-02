package vegancheckteam.plante_server.cmds.likes

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ProductLikeTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@Location("/like_product/")
data class LikeProductParams(
    val barcode: String,
    val testingNow: Long? = null,
)

fun likeProduct(params: LikeProductParams, user: User, testing: Boolean): Any {
    val now = now(testingNow = params.testingNow, testing)
    return transaction {
        val existingLikes = ProductLikeTable.select {
            (ProductLikeTable.barcode eq params.barcode) and
                    (ProductLikeTable.userId eq user.id)
        }.count()
        if (existingLikes <= 0) {
            ProductLikeTable.insert {
                it[userId] = user.id
                it[barcode] = params.barcode
                it[time] = now
            }
        }
        GenericResponse.success()
    }
}
