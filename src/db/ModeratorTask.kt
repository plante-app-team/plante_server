package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

enum class ModeratorTaskType(val persistentId: String) {
    USER_REPORT("user_report"),
    PRODUCT_CHANGE("product_change")
}

object ModeratorTask : Table("moderator_task") {
    val id = integer("id").autoIncrement()
    // NOTE: it doesn't reference a field in the Product table because
    // user can report a product from OFF.
    val productBarcode = text("barcode").index()
    val taskType = text("task_type")
    val taskSourceUserId = uuid("task_source_user_id").references(UserTable.id).index()
    val textFromUser = text("text_from_user").nullable()
    val time = long("time")
    override val primaryKey = PrimaryKey(id)
}
