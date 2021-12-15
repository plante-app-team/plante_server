package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class ProductAtShopSource(
    @JsonValue
    val persistentName: String,
    @JsonValue
    val persistentCode: Short,
) {
    MANUAL("manual", 1),
    OFF_SUGGESTION("off_suggestion", 2),
    RADIUS_SUGGESTION("radius_suggestion", 3);
    companion object {
        fun fromPersistentName(str: String): ProductAtShopSource? {
            return values().firstOrNull { it.persistentName == str }
        }
    }
}
