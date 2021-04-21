package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object UserQuizTable : Table("user_quiz") {
    val id = integer("id").autoIncrement()
    val question = text("question").index()
    val answer = text("answer")
    val userId = uuid("user_id").references(UserTable.id).index()
    val time = long("time")
    override val primaryKey = PrimaryKey(id)
}
