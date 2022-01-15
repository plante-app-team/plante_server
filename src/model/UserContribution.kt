package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.UserContributionTable

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserContribution(
    @JsonProperty("time_utc")
    val time: Long,
    @JsonProperty("type")
    val type: Short,
    @JsonProperty("barcode")
    val barcode: String?,
    @JsonProperty("shop_uid")
    val shopUID: OsmUID?) {
    companion object {
        fun from(tableRow: ResultRow) = UserContribution(
            time = tableRow[UserContributionTable.time],
            type = tableRow[UserContributionTable.type],
            barcode = tableRow[UserContributionTable.barcode],
            shopUID = tableRow[UserContributionTable.shopUID]?.let { OsmUID.from(it) })
    }
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
