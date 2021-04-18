package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.untitled_vegan_app_server.GlobalStorage
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ModeratorTask(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("barcode")
    val barcode: String,
    @JsonProperty("task_type")
    val taskType: ModeratorTaskType,
    @JsonProperty("task_source_user_id")
    val taskSourceUserId: String,
    @JsonProperty("text_from_user")
    val textFromUser: String?,
    @JsonProperty("creation_time")
    val creationTime: Long,
    @JsonProperty("assignee")
    val assignee: String?,
    @JsonProperty("assign_time")
    val assignTime: Long?) {
    companion object {
        fun from(tableRow: ResultRow) = ModeratorTask(
            id = tableRow[ModeratorTaskTable.id],
            barcode = tableRow[ModeratorTaskTable.productBarcode],
            taskType = taskTypeFrom(tableRow[ModeratorTaskTable.taskType])!!,
            taskSourceUserId = tableRow[ModeratorTaskTable.taskSourceUserId].toString(),
            textFromUser = tableRow[ModeratorTaskTable.textFromUser],
            creationTime = tableRow[ModeratorTaskTable.creationTime],
            assignee = tableRow[ModeratorTaskTable.assignee]?.toString(),
            assignTime = tableRow[ModeratorTaskTable.assignTime])
        private fun taskTypeFrom(code: Short) = ModeratorTaskType.fromPersistentCode(code)
    }
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
