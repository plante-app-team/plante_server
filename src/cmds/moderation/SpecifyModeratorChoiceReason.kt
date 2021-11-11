package vegancheckteam.plante_server.cmds.moderation

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup

@Location("/specify_moderator_choice_reason/")
data class SpecifyModeratorChoiceReasonParams(
    val barcode: String,
    val veganChoiceReasons: List<Int>? = null,
    val veganSourcesText: String? = null)

fun specifyModeratorChoiceReasonParams(params: SpecifyModeratorChoiceReasonParams, user: User): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }
    transaction {
        ProductTable.update({ ProductTable.barcode eq params.barcode }) {
            it[moderatorVeganChoiceReasons] = Product.moderatorChoiceReasonsToStr(params.veganChoiceReasons)
            it[moderatorVeganSourcesText] = params.veganSourcesText
        }
    }
    return GenericResponse.success()
}
