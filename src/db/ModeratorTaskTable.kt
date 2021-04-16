package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

enum class ModeratorTaskType(val persistentCode: Short) {
    USER_REPORT(1),
    PRODUCT_CHANGE(2)
}

object ModeratorTaskTable : Table("moderator_task") {
    val id = integer("id").autoIncrement()
    // NOTE: it doesn't reference a field in the Product table because
    // user can report a product from OFF.
    val productBarcode = text("barcode").index()
    val taskType = short("task_type")
    val taskSourceUserId = uuid("task_source_user_id").references(UserTable.id).index()
    val textFromUser = text("text_from_user").nullable()
    val time = long("time")
    override val primaryKey = PrimaryKey(id)
}
