package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.model.Gender
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@Location("/update_user_data/")
data class UpdateUserDataParams(
    val name: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val eatsMilk: Boolean? = null,
    val eatsEggs: Boolean? = null,
    val eatsHoney: Boolean? = null)

fun updateUserData(params: UpdateUserDataParams, user: User): Any {
    val gender: Gender? = if (params.gender != null) {
        val gender = Gender.fromStringName(params.gender)
        if (gender == null) {
            return GenericResponse.failure("invalid_gender", "Provided gender: ${params.gender}");
        }
        gender
    } else {
        null
    }

    if (params.birthday != null) {
        val expectedFormat = "dd.MM.yyyy"
        try {
            DateTimeFormatter.ofPattern(expectedFormat).parse(params.birthday)
        } catch (e: DateTimeParseException) {
            return GenericResponse.failure("invalid_date", "Provided date: ${params.birthday}");
        }
    }

    transaction {
        UserTable.update({ UserTable.id eq user.id }) { row ->
            params.name?.let { row[name] = it }
            gender?.let { row[UserTable.gender] = it.persistentCode }
            params.birthday?.let { row[birthday] = it }
            params.eatsMilk?.let { row[eatsMilk] = it }
            params.eatsEggs?.let { row[eatsEggs] = it }
            params.eatsHoney?.let { row[eatsHoney] = it }
        }
    }
    return GenericResponse.success()
}
