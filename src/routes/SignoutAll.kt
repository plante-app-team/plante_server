package vegancheckteam.untitled_vegan_app_server.routes

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/sign_out_all/")
data class SignOutAllParams(val unused: Int = 123)

fun signOutAll(unused: SignOutAllParams, user: User): Any {
    transaction {
        UserTable.update({ UserTable.id eq user.id }) { row ->
            row[loginGeneration] = user.loginGeneration + 1
        }
    }
    return HttpResponse.success("ok")
}
