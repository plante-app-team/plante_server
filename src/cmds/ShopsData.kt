package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmElementType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User

@Location("/shops_data/")
data class ShopsDataParams(val unused: Boolean? = true)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ShopsDataRequestBody(
    @JsonProperty("osm_ids")
    val osmIds: List<String>? = null,
    @JsonProperty("osm_uids")
    val osmUIDs: List<String>? = null)

fun shopsData(body: ShopsDataRequestBody) = transaction {
    val uids = if (body.osmUIDs != null) {
        body.osmUIDs.map { OsmUID.from(it) }
    } else if (body.osmIds != null) {
        body.osmIds.map { OsmUID.from(OsmElementType.NODE, it) }
    } else {
        return@transaction GenericResponse.failure("wtf")
    }

    val shops = ShopTable.select {
        ShopTable.osmUID inList uids.map { it.asStr }
    }.map { Shop.from(it) }
    ShopsDataResponse(shops.associateBy { it.osmId }, shops.associateBy { it.osmUID })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ShopsDataResponse(
    @JsonProperty("results")
    val shops: Map<String, Shop>,
    @JsonProperty("results_v2")
    val shopsV2: Map<OsmUID, Shop>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
