package vegancheckteam.plante_server.model

enum class ShopValidationReason(val persistentCode: Short) {
    COORDS_WERE_NULL(1),
    NEVER_VALIDATED_BEFORE(2),
    SHOP_MOVED(3);
    companion object {
        fun fromPersistentCode(code: Short) = VegStatus.values().find { it.persistentCode == code }
    }
}
