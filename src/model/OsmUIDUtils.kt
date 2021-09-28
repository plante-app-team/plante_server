package vegancheckteam.plante_server.model

fun convertOsmIdentifier(osmId: String): OsmUID {
    return OsmUID.from(OsmElementType.NODE, osmId)
}
