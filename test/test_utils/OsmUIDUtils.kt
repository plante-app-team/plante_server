package test_utils

import java.util.UUID
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID

fun generateFakeOsmUID(postfix: Any = "") = OsmUID.from(
    OsmElementType.NODE,
    UUID.randomUUID().toString() + postfix.toString())
