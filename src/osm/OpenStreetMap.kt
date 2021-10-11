package vegancheckteam.plante_server.osm

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID

object OpenStreetMap {
    suspend fun requestShopsFor(uids: List<OsmUID>, httpClient: HttpClient): Set<OsmShop> {
        val json = requestsShopsJsonFor(uids, httpClient)
        return shopsJsonToShops(json)
    }

    private suspend fun requestsShopsJsonFor(uids: List<OsmUID>, httpClient: HttpClient): Map<*, *> {
        Log.i("OpenStreetMap", "requestsShopsJsonFor uids: $uids")
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
        val response = httpClient.get<HttpResponse>(
                urlString = "https://lz4.overpass-api.de/api/interpreter?data=$cmd")

        Log.i("OpenStreetMap", "requestsShopsJsonFor response: $response")

        @Suppress("BlockingMethodInNonBlockingContext")
        return ObjectMapper().readValue(response.readText(), MutableMap::class.java)
    }

    private fun shopsJsonToShops(json: Map<*, *>): Set<OsmShop> {
        val elements = json["elements"]
        if (elements !is List<*>) {
            throw IllegalArgumentException("No 'elements' in JSON: $json")
        }
        val result = mutableSetOf<OsmShop>()
        for (element in elements) {
            if (element !is Map<*, *>) {
                Log.w("OpenStreetMap", "shopsJsonToShops, element is not a map: $element")
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
                Log.w("OpenStreetMap", "shopsJsonToShops, element lacks data: $type $id $lat $lon")
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
