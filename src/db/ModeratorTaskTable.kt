package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ModeratorTaskTable : Table("moderator_task") {
    val id = integer("id").autoIncrement()
    val productBarcode = text("barcode").nullable().index()
    val osmId = text("osmId").nullable().index()
    val taskType = short("task_type")
    val taskSourceUserId = uuid("task_source_user_id").references(UserTable.id).index()
    val textFromUser = text("text_from_user").nullable()
    val creationTime = long("creation_time")

    val assignee = uuid("assignee").references(UserTable.id).nullable().index()
    val assignTime = long("assign_time").nullable().index()
    val resolutionTime = long("resolution_time").nullable().index()

    val rejectedAssigneesList = text("rejected_assignees_list").nullable()

    val lang = text("lang").nullable().index()

    override val primaryKey = PrimaryKey(id)
}
