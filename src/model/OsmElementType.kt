package vegancheckteam.plante_server.model

enum class OsmElementType(
    val persistentCode: Short,
    val osmName: String) {
    NODE(1, "node"),
    RELATION(2, "relation"),
    WAY(3, "way");
    companion object {
        fun fromPersistentCode(code: Short) = values().find { it.persistentCode == code }
        fun fromString(str: String) = values().find { it.osmName == str }
    }
    fun makeUID(osmId: String) = OsmUID.from(this, osmId)
}
