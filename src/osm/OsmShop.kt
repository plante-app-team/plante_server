package vegancheckteam.plante_server.osm

import vegancheckteam.plante_server.model.OsmUID

data class OsmShop(
    val uid: OsmUID,
    val lat: Double,
    val lon: Double,
)
