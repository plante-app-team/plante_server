package vegancheckteam.plante_server.responses.model

import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.model.ModeratorTask

data class ModeratorTasksDataResponse(
    @JsonProperty("tasks")
    val tasks: List<ModeratorTask>)
