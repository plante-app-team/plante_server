package vegancheckteam.plante_server.model

enum class OsmElementType(
    val persistentCode: Short) {
    NODE(1),
    RELATION(2),
    WAY(3);
    companion object {
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
    }
    fun makeUID(osmId: String) = OsmUID.from(this, osmId)
}
