package vegancheckteam.untitled_vegan_app_server.responses.model

import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.untitled_vegan_app_server.model.ModeratorTask

data class ModeratorTasksDataResponse(
    @JsonProperty("tasks")
    val tasks: List<ModeratorTask>)