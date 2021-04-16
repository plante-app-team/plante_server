package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class VegStatusSource(
        @JsonValue val sourceName: String,
        val persistentCode: Short) {
    OPEN_FOOD_FACTS("open_food_facts", 1),
    COMMUNITY("community", 2),
    MODERATOR("moderator", 3),
    UNKNOWN("unknown", 4);
    companion object {
        fun fromStringName(str: String) = values().find { it.sourceName == str.toLowerCase() }
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
