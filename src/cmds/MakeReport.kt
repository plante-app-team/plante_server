package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.UserContributionTable
import vegancheckteam.plante_server.model.UserContributionType

const val MAX_REPORTS_FOR_PRODUCT = 100
const val MAX_REPORTS_FOR_PRODUCT_TESTING = 10
const val MAX_REPORTS_FOR_USER = 100
const val MAX_REPORTS_FOR_USER_TESTING = 10
const val REPORT_TEXT_MAX_LENGTH = 256
const val REPORT_TEXT_MIN_LENGTH = 3

@Location("/make_report/")
data class MakeReportParams(
    val barcode: String,
    val text: String,
    val testingNow: Long? = null)

fun makeReport(params: MakeReportParams, user: User, testing: Boolean): Any {
    val maxReportsForUser = if (testing) MAX_REPORTS_FOR_USER_TESTING else MAX_REPORTS_FOR_USER
    val existingTasksOfUser = transaction {
        val existingModeratorTasksOfUser = ModeratorTaskTable.select {
            (ModeratorTaskTable.taskSourceUserId eq user.id) and
                    (ModeratorTaskTable.resolutionTime eq null)
        }
        existingModeratorTasksOfUser.count()
    }
    if (existingTasksOfUser >= maxReportsForUser) {
        return GenericResponse.failure(
            "too_many_reports_for_user",
            "User sent too many reports: $existingTasksOfUser")
    }

    val maxReportsForProduct = if (testing) MAX_REPORTS_FOR_PRODUCT_TESTING else MAX_REPORTS_FOR_PRODUCT
    val existingTasksOfProduct = transaction {
        val existingModeratorTasksOfProduct = ModeratorTaskTable.select {
            (ModeratorTaskTable.productBarcode eq params.barcode) and
                    (ModeratorTaskTable.resolutionTime eq null)
        }
        existingModeratorTasksOfProduct.count()
    }
    if (existingTasksOfProduct >= maxReportsForProduct) {
        return GenericResponse.failure(
            "too_many_reports_for_product",
            "Product received too many reports: $existingTasksOfProduct")
    }

    if (params.text.length < REPORT_TEXT_MIN_LENGTH) {
        return GenericResponse.failure("report_text_too_short")
    }
    if (REPORT_TEXT_MAX_LENGTH < params.text.length) {
        return GenericResponse.failure("report_text_too_long")
    }

    val now = now(testingNow = params.testingNow, testing)
    transaction {
        ModeratorTaskTable.insert {
            it[productBarcode] = params.barcode
            it[taskType] = ModeratorTaskType.USER_REPORT.persistentCode
            it[taskSourceUserId] = user.id
            it[textFromUser] = params.text
            it[creationTime] = now
        }
        UserContributionTable.add(
            user,
            UserContributionType.PRODUCT_REPORTED,
            now,
            barcode = params.barcode,
        )
    }
    return GenericResponse.success()
}
