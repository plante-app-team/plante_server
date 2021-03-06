package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonValue

enum class ModeratorTaskType(
        @JsonValue
        val typeName: String,
        val persistentCode: Short,
        val priority: Short /** the lower the better **/) {
    USER_PRODUCT_REPORT("user_report", 1, 1),
    PRODUCT_CHANGE("product_change", 2, 4),
    OSM_SHOP_CREATION("osm_shop_creation", 3, 3),
    OSM_SHOP_NEEDS_MANUAL_VALIDATION("osm_shop_needs_manual_validation", 4, 2),
    PRODUCT_CHANGE_IN_OFF("product_change_in_off", 5, 10),
    USER_FEEDBACK("user_feedback", 6, 1),
    USER_NEWS_PIECE_REPORT("user_news_piece_report", 7, 1),
    CUSTOM_MODERATION_ACTION("custom_moderation_action", 100, 100),
    ;
    companion object {
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
        fun fromTaskTypeName(name: String) = values().find { it.typeName == name }
    }
}
