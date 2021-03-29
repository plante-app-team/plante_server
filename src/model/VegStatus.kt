package vegancheckteam.untitled_vegan_app_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class VegStatus(@JsonValue val statusName: String) {
    POSITIVE("positive"),
    NEGATIVE("negative"),
    POSSIBLE("possible"),
    UNKNOWN("unknown");
    companion object {
        fun fromStringName(str: String): VegStatus? {
            for (status in values()) {
                if (status.statusName == str.toLowerCase()) {
                    return status;
                }
            }
            return null
        }
    }
}