package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class VegStatus(
        @JsonValue val statusName: String,
        val persistentCode: Short) {
    POSITIVE("positive", 1),
    NEGATIVE("negative", 2),
    POSSIBLE("possible", 3),
    UNKNOWN("unknown", 4);
    companion object {
        fun fromStringName(str: String) = values().find { it.statusName == str.lowercase() }
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
