package vegancheckteam.plante_server.model

enum class UserContributionType(
    val persistentCode: Short,
) {
    PRODUCT_EDITED(1),
    PRODUCT_ADDED_TO_SHOP(2),
    PRODUCT_REPORTED(3),
    SHOP_CREATED(4),
    LEGACY_PRODUCT_EDITED(5);
    companion object {
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
}
