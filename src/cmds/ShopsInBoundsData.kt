package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.JoinType
import kotlin.math.absoluteValue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.AreaTooBigException
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.geoSelect
import vegancheckteam.plante_server.base.kmToGrad
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Shop

@Location("/shops_in_bounds_data/")
data class ShopsInBoundsDataParams(
    val west: Double,
    val east: Double,
    val north: Double,
    val south: Double)

fun shopsInBoundsData(params: ShopsInBoundsDataParams) = transaction {
    val withinBounds = try {
        geoSelect(
            params.west,
            params.east,
            params.north,
            params.south,
            ShopTable.lat,
            ShopTable.lon,
            maxSize = kmToGrad(500.0)
        )
    } catch (e: AreaTooBigException) {
        Log.w("/shops_in_bounds_data/", "Shops from too big area are requested: $params")
        return@transaction GenericResponse.failure("area_too_big")
    }

    val shops = ShopTable
        .select(withinBounds)
        .map { Shop.from(it) }

    val wantedProducts = (ProductAtShopTable.shopId inList shops.map { it.id }) and ProductTable.nothingNonVegan
    val selectionWithBarcodes = ProductAtShopTable.join(
        ProductTable,
        joinType = JoinType.LEFT,
        onColumn = ProductAtShopTable.productId,
        otherColumn = ProductTable.id,
    ).select(wantedProducts)
    val barcodesMap = mutableMapOf<OsmUID, MutableList<String>>()
    for (shop in shops) {
        barcodesMap[shop.osmUID] = mutableListOf()
    }

    val shopsIdsMap = shops.associateBy { it.id }
    for (row in selectionWithBarcodes) {
        val shop = shopsIdsMap[row[ProductAtShopTable.shopId]] ?: continue
        barcodesMap[shop.osmUID]?.add(row[ProductTable.barcode])
    }

    val shopsMap = shops.associateBy { it.osmUID }
    ShopsInBoundsDataResponse(shopsMap, barcodesMap)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ShopsInBoundsDataResponse(
    @JsonProperty("results")
    val shops: Map<OsmUID, Shop>,
    @JsonProperty("barcodes")
    val barcodes: Map<OsmUID, List<String>>) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
