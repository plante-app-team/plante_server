package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ModeratorTasksDataResponse(
    @JsonProperty("tasks")
    val tasks: List<ModeratorTask>)
