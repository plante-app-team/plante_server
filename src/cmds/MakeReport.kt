package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.UserContributionTable
import vegancheckteam.plante_server.model.UserContributionType

const val REPORT_TEXT_MAX_LENGTH = 10000
const val REPORT_TEXT_MIN_LENGTH = 3

@Location("/make_report/")
data class MakeReportParams(
    val text: String,
    val barcode: String? = null,
    val newsPieceId: Int? = null,
    val testingNow: Long? = null,
)

fun makeReport(params: MakeReportParams, user: User, testing: Boolean): Any {
    if (params.text.length < REPORT_TEXT_MIN_LENGTH) {
        return GenericResponse.failure("report_text_too_short")
    }
    if (REPORT_TEXT_MAX_LENGTH < params.text.length) {
        return GenericResponse.failure("report_text_too_long")
    }

    val now = now(testingNow = params.testingNow, testing)
    return transaction {
        if (params.barcode != null) {
            ModeratorTaskTable.insert {
                it[productBarcode] = params.barcode
                it[taskType] = ModeratorTaskType.USER_PRODUCT_REPORT.persistentCode
                it[taskSourceUserId] = user.id
                it[textFromUser] = params.text
                it[creationTime] = now
            }
            UserContributionTable.add(
                user,
                UserContributionType.REPORT_WAS_MADE,
                now,
                barcode = params.barcode,
            )
        } else if (params.newsPieceId != null) {
            val newsPieceExists = NewsPieceTable.select(NewsPieceTable.id eq params.newsPieceId).count() > 0
            if (!newsPieceExists) {
                return@transaction GenericResponse.failure(
                    "news_piece_not_found",
                    "no news pieces with ID ${params.newsPieceId} exist",
                )
            }
            ModeratorTaskTable.insert {
                it[newsPieceId] = params.newsPieceId
                it[taskType] = ModeratorTaskType.USER_NEWS_PIECE_REPORT.persistentCode
                it[taskSourceUserId] = user.id
                it[textFromUser] = params.text
                it[creationTime] = now
            }
            UserContributionTable.add(
                user,
                UserContributionType.REPORT_WAS_MADE,
                now,
                newsPieceID = params.newsPieceId,
            )
        } else {
            return@transaction GenericResponse.failure("invalid_params", "needed params were not provided")
        }

        GenericResponse.success()
    }

}
