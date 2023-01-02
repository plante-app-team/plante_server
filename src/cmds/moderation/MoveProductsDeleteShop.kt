package vegancheckteam.plante_server.cmds.moderation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.base64.Base64
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.cmds.PutProductToShopParams
import vegancheckteam.plante_server.cmds.putProductToShop
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.db.from
import vegancheckteam.plante_server.db.select2
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserRightsGroup
import vegancheckteam.plante_server.osm.OpenStreetMap
import vegancheckteam.plante_server.osm.OsmShop

@Location("/move_products_delete_shop/")
data class MoveProductsDeleteShopParams(
    val badOsmUID: String,
    val goodOsmUID: String,
    val productionOsmDb: Boolean = true,
    val testingResponsesJsonBase64: String? = null,
    val testingNow: Long? = null)

suspend fun moveProductsDeleteShop(params: MoveProductsDeleteShopParams, user: User, testing: Boolean, client: HttpClient): Any {
    if (user.userRightsGroup.persistentCode < UserRightsGroup.CONTENT_MODERATOR.persistentCode) {
        return GenericResponse.failure("denied")
    }

    // Get OSM shops
    val (goodOsmShop, badOsmShop) = getOsmShopsPair(params.goodOsmUID, params.badOsmUID, client, params)
    if (goodOsmShop == null) {
        return GenericResponse.failure("shop_not_found", "OSM UID: ${params.goodOsmUID}")
    }

    // Get bad shop
    val badShopRow = transaction {
        ShopTable
            .select(ShopTable.osmUID eq params.badOsmUID)
            .firstOrNull()
    }
    if (badShopRow != null && !badShopRow[ShopTable.createdNewOsmNode]) {
        return GenericResponse.failure("wont_delete_shop_not_created_by_us", "OSM UID: ${params.badOsmUID}")
    }
    val badShop = badShopRow?.let { Shop.from(it) }
    if (badShop == null) {
        return GenericResponse.failure("shop_not_found", "OSM UID: ${params.badOsmUID}")
    }

    // Move products
    moveProducts(user, params, badShop, goodOsmShop, testing)

    // Delete bad shop locally
    deleteShopLocally(DeleteShopLocallyParams(badShop.osmUID.asStr), user)

    // Delete bad shop remotely
    if (badOsmShop != null) {
        return deleteBadOsmShop(badOsmShop, params, testing, client)
    }
    return GenericResponse.success()
}

private suspend fun getOsmShopsPair(goodShopUID: String, badShopUID: String, client: HttpClient, params: MoveProductsDeleteShopParams): Pair<OsmShop?, OsmShop?> {
    if (params.testingResponsesJsonBase64 != null) {
        val testingResponseJson = String(Base64.decode(params.testingResponsesJsonBase64))
        val osmResponses = MoveProductsDeleteShopTestingOsmResponses.from(testingResponseJson)
        var badShop: OsmShop? = null
        var goodShop: OsmShop? = null
        if (osmResponses.goodShopFound) {
            val lat = osmResponses.goodShopLat
            val lon = osmResponses.goodShopLon
            goodShop = OsmShop(OsmUID.from(goodShopUID), lat, lon, "1")
        }
        if (osmResponses.badShopFound) {
            badShop = OsmShop(OsmUID.from(badShopUID), 1.0, 2.0, "1")
        }
        return Pair(goodShop, badShop)
    }
    val osmShops = OpenStreetMap
        .requestShopsFor(listOf(
            OsmUID.from(badShopUID),
            OsmUID.from(goodShopUID)),
            client)
    val goodShop = osmShops.find { it.uid.asStr == goodShopUID }
    val badShop = osmShops.find { it.uid.asStr == badShopUID }
    return Pair(goodShop, badShop)
}

private fun moveProducts(
        by: User,
        params: MoveProductsDeleteShopParams,
        from: Shop,
        to: OsmShop,
        testing: Boolean) = transaction {
    val productsAtShopRows = ProductAtShopTable.select(ProductAtShopTable.shopId eq from.id)
    for (row in productsAtShopRows) {
        val productId = row[ProductAtShopTable.productId]
        val product = ProductTable
            .select2(by) { ProductTable.id eq productId }
            .map { Product.from(it) }
            .firstOrNull()
        if (product == null) {
            Log.e("/move_products_delete_shop/", "Somehow product $productId was not found")
            continue
        }
        val authorId = row[ProductAtShopTable.creatorUserId]
        if (authorId == null) {
            Log.e("/move_products_delete_shop/", "Somehow author which added product to a shop is not found")
            continue
        }
        val author = UserTable.select(UserTable.id eq authorId).map { User.from(it) }.firstOrNull()
        if (author == null) {
            Log.e("/move_products_delete_shop/", "Somehow author $authorId was not found")
            continue
        }
        val moveParams = PutProductToShopParams(
            barcode = product.barcode,
            shopOsmUID = to.uid.asStr,
            testingNow = params.testingNow,
            lat = to.lat,
            lon = to.lon,
        )
        // NOTE: we the putProductToShop call will add a vote with
        // the current time. It's not very nice, but at the moment
        // of writing this code wasn't worth fixing.
        putProductToShop(moveParams, author, testing)
    }
    ProductAtShopTable.deleteWhere {
        ProductAtShopTable.shopId eq from.id
    }
    ShopTable.update({ShopTable.id eq from.id}) {
        it[productsCount] = 0
    }
}

private suspend fun deleteBadOsmShop(
        badOsmShop: OsmShop,
        params: MoveProductsDeleteShopParams,
        testing: Boolean,
        client: HttpClient): Any {
    val (osmUrl, osmUser, osmPass) = if (params.productionOsmDb) {
        Triple("https://www.openstreetmap.org",
            Config.instance.osmProdUser,
            Config.instance.osmProdPassword)
    } else {
        Triple("https://master.apis.dev.openstreetmap.org",
            Config.instance.osmTestingUser,
            Config.instance.osmTestingPassword)

    }
    val osmCredentials = String(Base64.encode("$osmUser:$osmPass".toByteArray()))

    val testingResponse = if (testing && params.testingResponsesJsonBase64 != null) {
        val testingResponseJson = String(Base64.decode(params.testingResponsesJsonBase64))
        MoveProductsDeleteShopTestingOsmResponses.from(testingResponseJson)
    } else {
        null
    }

    // Start OSM shop deletion
    val osmChangesetId = if (testingResponse != null) {
        testingResponse.createChangesetResp
    } else {
        val resp = client.put<HttpResponse>("$osmUrl/api/0.6/changeset/create") {
            header("Authorization", "Basic $osmCredentials")
            body = """
                <osm>
                  <changeset>
                    <tag k="created_by" v="planteuser"/>
                    <tag k="comment" v="Postmoderation - deletion of a problematic organization created by a Plante app user"/>
                  </changeset>
                </osm>
                """.trimIndent()
        }
        if (resp.status.value != 200) {
            Log.w("/move_products_delete_shop/", "osm_error: $resp")
            return GenericResponse.failure("osm_error", resp.status.toString())
        }
        resp.readText()
    }

    // Put shop deletion into the OSM changeset
    if (testingResponse != null) {
        testingResponse.deleteShopResp
    } else {
        val resp = client.delete<HttpResponse>("$osmUrl/api/0.6/node/${badOsmShop.uid.osmId}") {
            header("Authorization", "Basic $osmCredentials")
            body = """
                <osm>
                  <node
                    changeset="$osmChangesetId"
                    id="${badOsmShop.uid.osmId}"
                    version="${badOsmShop.version}"
                    lat="${badOsmShop.lat}"
                    lon="${badOsmShop.lon}"/>
                </osm>
                """.trimIndent()
        }
        if (resp.status.value != 200) {
            Log.w("/move_products_delete_shop/", "osm_error: $resp")
            return GenericResponse.failure("osm_error", resp.status.toString())
        }
        resp.readText()
    }

    if (testingResponse != null) {
        testingResponse.closeChangesetResp
    } else {
        val resp = client.put<HttpResponse>("$osmUrl/api/0.6/changeset/$osmChangesetId/close") {
            header("Authorization", "Basic  $osmCredentials")
        }
        if (resp.status.value != 200) {
            Log.w("/move_products_delete_shop/", "osm_error: $resp")
            return GenericResponse.failure("osm_error", resp.status.toString())
        }
        resp.readText()
    }
    return GenericResponse.success()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MoveProductsDeleteShopTestingOsmResponses(
    @JsonProperty("create_changeset_resp")
    val createChangesetResp: String,
    @JsonProperty("delete_shop_resp")
    val deleteShopResp: String,
    @JsonProperty("close_changeset_resp")
    val closeChangesetResp: String,
    @JsonProperty("good_shop_found")
    val goodShopFound: Boolean,
    @JsonProperty("bad_shop_found")
    val badShopFound: Boolean,
    @JsonProperty("good_shop_lat")
    val goodShopLat: Double = 1.0,
    @JsonProperty("good_shop_lon")
    val goodShopLon: Double = 2.0) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        fun from(jsonString: String): MoveProductsDeleteShopTestingOsmResponses =
            GlobalStorage.jsonMapper.readValue(jsonString, MoveProductsDeleteShopTestingOsmResponses::class.java)
    }
}
