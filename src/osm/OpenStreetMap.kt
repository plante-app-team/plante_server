package vegancheckteam.plante_server.osm

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID

object OpenStreetMap {
    suspend fun requestShopsFor(uids: List<OsmUID>, httpClient: HttpClient): Set<OsmShop> {
        val json = requestsShopsJsonFor(uids, httpClient)
        return shopsJsonToShops(json)
    }

    private suspend fun requestsShopsJsonFor(uids: List<OsmUID>, httpClient: HttpClient): Map<*, *> {
        val nodesIds = uids.filter { it.elementType == OsmElementType.NODE }.map { it.osmId }
        val waysIds = uids.filter { it.elementType == OsmElementType.WAY }.map { it.osmId }
        val relationsIds = uids.filter { it.elementType == OsmElementType.RELATION }.map { it.osmId }
        val nodeCmdPiece = if (nodesIds.isNotEmpty()) {
            "node(id:${nodesIds.joinToString(",")});"
        } else {
            ""
        }
        val wayCmdPiece = if (waysIds.isNotEmpty()) {
            "way(id:${waysIds.joinToString(",")});"
        } else {
            ""
        }
        val relationCmdPiece = if (relationsIds.isNotEmpty()) {
            "relation(id:${relationsIds.joinToString(",")});"
        } else {
            ""
        }
        val cmd = "[out:json];($nodeCmdPiece$wayCmdPiece$relationCmdPiece);out center;"
        // TODO(https://trello.com/c/XgGFE05M/): log info (response)
        val response = httpClient.get<String>(
                urlString = "https://lz4.overpass-api.de/api/interpreter?data=$cmd")
        @Suppress("BlockingMethodInNonBlockingContext")
        return ObjectMapper().readValue(response, MutableMap::class.java)
    }

    private fun shopsJsonToShops(json: Map<*, *>): Set<OsmShop> {
        val elements = json["elements"]
        if (elements !is List<*>) {
            throw IllegalArgumentException("No 'elements' in JSON: $json")
        }
        val result = mutableSetOf<OsmShop>()
        for (element in elements) {
            if (element !is Map<*, *>) {
                // TODO(https://trello.com/c/XgGFE05M/): log error
                continue
            }
            val typeStr = element["type"]?.toString()
            val type = typeStr?.let { OsmElementType.fromString(it) }
            val id = element["id"]?.toString()
            val center = element["center"] as Map<*, *>?
            val (lat, lon) = if (center != null) {
                Pair(center["lat"] as Double?, center["lon"] as Double?)
            } else {
                Pair(element["lat"] as Double?, element["lon"] as Double?)
            }
            if (type == null || id == null || lat == null || lon == null) {
                // TODO(https://trello.com/c/XgGFE05M/): log error
                continue
            }
            result += OsmShop(
                OsmUID.from(type, id),
                lat,
                lon,
            )
        }
        return result
    }
}
