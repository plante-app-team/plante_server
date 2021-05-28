package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User

@Location("/shops_data/")
data class ShopsDataParams(val unused: Boolean? = true)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ShopsDataRequestBody(
    @JsonProperty("osm_ids")
    val osmIds: List<String>)

fun shopsData(params: ShopsDataParams, body: ShopsDataRequestBody, user: User) = transaction {
    val shops = ShopTable.select {
        ShopTable.osmId inList body.osmIds
    }.map { Shop.from(it) }
    ShopsDataResponse(shops.associateBy { it.osmId })
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ShopsDataResponse(
    @JsonProperty("results")
    val shops: Map<String, Shop>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
