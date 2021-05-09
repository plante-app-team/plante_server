package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage.jsonMapper
import vegancheckteam.plante_server.db.ShopTable

data class Shop(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("osm_id")
    val osmId: String) {
    companion object {
        fun from(tableRow: ResultRow) = Shop(
            id = tableRow[ShopTable.id],
            osmId = tableRow[ShopTable.osmId])
    }
}
