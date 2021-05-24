package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class ModeratorTaskType(
        @JsonValue
        val taskName: String,
        val persistentCode: Short,
        val priority: Short /** the lower the better **/) {
    USER_REPORT("user_report", 1, 1),
    OSM_SHOP_CREATION("osm_shop_creation", 2, 2),
    PRODUCT_CHANGE("product_change", 3, 3);
    companion object {
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
