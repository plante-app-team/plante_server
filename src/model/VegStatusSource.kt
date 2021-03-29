package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class VegStatusSource(@JsonValue val sourceName: String) {
    OPEN_FOOD_FACTS("open_food_facts"),
    COMMUNITY("community"),
    MODERATOR("moderator"),
    UNKNOWN("unknown");
    companion object {
        fun fromStringName(str: String): VegStatusSource? {
            for (source in values()) {
                if (source.sourceName == str.toLowerCase()) {
                    return source;
                }
            }
            return null
        }
    }
}
