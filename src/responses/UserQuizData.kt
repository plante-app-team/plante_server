package vegancheckteam.plante_server.responses

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.UserQuizTable
import vegancheckteam.plante_server.model.User

@Location("/user_quiz_data/")
data class UserQuizDataParams(val unused: String = "")

fun userQuizData(user: User): Any {
    return transaction {
        val rows = UserQuizTable.select {
            UserQuizTable.userId eq user.id
        }
        val questions = mutableListOf<String>()
        val answers = mutableListOf<String>()
        for (row in rows) {
            questions.add(row[UserQuizTable.question])
            answers.add(row[UserQuizTable.answer])
        }
        UserQuizDataResponse(questions, answers)
    }
}

data class UserQuizDataResponse(
    @JsonProperty("questions")
    val questions: List<String>,
    @JsonProperty("answers")
    val answers: List<String>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
