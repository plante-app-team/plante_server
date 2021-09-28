package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonValue

class OsmUID private constructor(val asStr: String) {
    companion object {
        fun from(asString: String) = OsmUID(asString)
        fun from(type: OsmElementType, osmId: String) = OsmUID("${type.persistentCode}:$osmId")
        fun fromEitherOf(strOsmUID: String?, osmId: String?): OsmUID? {
            return if (strOsmUID != null) {
                from(strOsmUID)
            } else if (osmId != null) {
                convertOsmIdentifier(osmId)
            } else {
                null
            }
        }
    }

    val osmId by lazy { asStr.substring(2) }
    val elementType by lazy {
        var code = asStr.substring(0, 1).toShortOrNull()
        if (code == null) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            code = OsmElementType.NODE.persistentCode
        }
        var type = OsmElementType.fromPersistentCode(code as Short)
        if (type == null) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            type = OsmElementType.NODE
        }
        type
    }

    @JsonValue
    override fun toString() = asStr
    override fun hashCode() = asStr.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other is OsmUID) {
            return asStr == other.asStr
        }
        return false
    }
}
