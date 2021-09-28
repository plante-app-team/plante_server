package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.db.ShopTable

data class Shop(
    @JsonProperty("id")
    val id: Int,
    /**
     * See [ShopTable.osmUID].
     */
    @JsonProperty("osm_uid")
    val osmUID: OsmUID,
    @JsonProperty("osm_id")
    val osmId: String,
    @JsonProperty("products_count")
    val productsCount: Int) {
    companion object {
        fun from(tableRow: ResultRow): Shop {
            val osmUID = OsmUID.from(tableRow[ShopTable.osmUID])
            return Shop(
                id = tableRow[ShopTable.id],
                osmUID = osmUID,
                osmId = osmUID.osmId,
                productsCount = tableRow[ShopTable.productsCount])
        }
    }
}
