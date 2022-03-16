package vegancheckteam.plante_server.model.news

enum class NewsPieceType(
    val persistentCode: Short,
) {
    PRODUCT_AT_SHOP(1);
    companion object {
        fun fromPersistentCode(code: Short): NewsPieceType? {
            return values().firstOrNull { it.persistentCode == code }
        }
    }
}
