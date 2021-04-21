package vegancheckteam.plante_server.responses

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.db.UserQuizTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import java.time.ZonedDateTime

// So that a malicious user wouldn't overfill the DB
const val MAX_QUIZ_ANSWERS_COUNT = 10;

@Location("/user_quiz/")
data class UserQuizParams(val question: String, val answer: String)

fun userQuiz(params: UserQuizParams, user: User): Any {
    val existingAnswers = transaction {
        val existingAnswers = UserQuizTable.select {
            UserQuizTable.userId eq user.id
        }
        existingAnswers.count()
    }
    if (existingAnswers >= MAX_QUIZ_ANSWERS_COUNT) {
        return GenericResponse.failure("too_many_answers", "User has too many answers: $existingAnswers")
    }

    transaction {
        UserQuizTable.deleteWhere {
            UserQuizTable.question eq params.question
        }
        UserQuizTable.insert {
            it[userId] = user.id
            it[time] = ZonedDateTime.now().toEpochSecond()
            it[question] = params.question
            it[answer] = params.answer
        }
    }
    return GenericResponse.success()
}
