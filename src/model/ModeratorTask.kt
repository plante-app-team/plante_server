package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ModeratorTaskTable

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ModeratorTask(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("barcode")
    val barcode: String?,
    @JsonProperty("osm_uid")
    val osmUID: OsmUID?,
    @JsonProperty("news_piece_id")
    val newsPieceId: Int?,
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
    val assignTime: Long?,
    @JsonProperty("resolution_time")
    val resolutionTime: Long?,
    @JsonProperty("resolver")
    val resolver: String?,
    @JsonProperty("resolver_action")
    val resolverAction: String?,
    @JsonProperty("rejected_assignees_list")
    val rejectedAssigneesList: List<UUID>,
    @JsonProperty("lang")
    val lang: String?) {
    companion object {
        fun from(tableRow: ResultRow) = ModeratorTask(
            id = tableRow[ModeratorTaskTable.id],
            barcode = tableRow[ModeratorTaskTable.productBarcode],
            osmUID = tableRow[ModeratorTaskTable.osmUID]?.let { OsmUID.from(it) },
            newsPieceId = tableRow[ModeratorTaskTable.newsPieceId],
            taskType = taskTypeFrom(tableRow[ModeratorTaskTable.taskType])!!,
            taskSourceUserId = tableRow[ModeratorTaskTable.taskSourceUserId].toString(),
            textFromUser = tableRow[ModeratorTaskTable.textFromUser],
            creationTime = tableRow[ModeratorTaskTable.creationTime],
            assignee = tableRow[ModeratorTaskTable.assignee]?.toString(),
            assignTime = tableRow[ModeratorTaskTable.assignTime],
            resolutionTime = tableRow[ModeratorTaskTable.resolutionTime],
            resolver = tableRow[ModeratorTaskTable.resolver]?.toString(),
            resolverAction = tableRow[ModeratorTaskTable.resolverAction],
            rejectedAssigneesList = (tableRow[ModeratorTaskTable.rejectedAssigneesList] ?: "")
                .split(",")
                .filter { it.isNotEmpty() }
                .map { UUID.fromString(it) },
            lang = tableRow[ModeratorTaskTable.lang])
        private fun taskTypeFrom(code: Short) = ModeratorTaskType.fromPersistentCode(code)
    }
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    fun joinRejectedAssignees(): String = rejectedAssigneesList.joinToString(",")
    fun addRejectedAssignee(assignee: UUID): ModeratorTask {
        val updatedRejectedAssigneesList = rejectedAssigneesList.toMutableList()
        if (!rejectedAssigneesList.contains(assignee)) {
            updatedRejectedAssigneesList += assignee
        }
        return copy(rejectedAssigneesList = updatedRejectedAssigneesList)
    }
}
