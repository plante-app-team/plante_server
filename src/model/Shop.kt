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
    val productsCount: Int,
    @JsonProperty("lat")
    val lat: Double?,
    @JsonProperty("lon")
    val lon: Double?,
    @JsonProperty("deleted")
    val deleted: Boolean?) {
    companion object {
        fun from(tableRow: ResultRow): Shop {
            val osmUID = OsmUID.from(tableRow[ShopTable.osmUID])
            return Shop(
                id = tableRow[ShopTable.id],
                osmUID = osmUID,
                osmId = osmUID.osmId,
                productsCount = tableRow[ShopTable.productsCount],
                lat = tableRow[ShopTable.lat],
                lon = tableRow[ShopTable.lon],
                deleted = if (tableRow[ShopTable.deleted]) true else null)
        }
    }
}
