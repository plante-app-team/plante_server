package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.UserContributionTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.ProductAtShopSource
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.ShopValidationReason
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserContributionType
import vegancheckteam.plante_server.model.news.NewsPieceType

@Location("/put_product_to_shop/")
data class PutProductToShopParams(
    val barcode: String,
    val shopOsmId: String? = null,
    val shopOsmUID: String? = null,
    val source: String = ProductAtShopSource.MANUAL.persistentName,
    val testingNow: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null)

fun putProductToShop(params: PutProductToShopParams, user: User, testing: Boolean) = transaction {
    val osmUID = OsmUID.fromEitherOf(params.shopOsmUID, params.shopOsmId)
    if (osmUID == null) {
        return@transaction GenericResponse.failure("wtf")
    }
    val source = ProductAtShopSource.fromPersistentName(params.source)
    if (source == null) {
        return@transaction GenericResponse.failure("invalid_source", "Invalid source: ${params.source}")
    }

    val existingProduct = ProductTable.select { ProductTable.barcode eq params.barcode }.firstOrNull()
    val productId = if (existingProduct != null) {
        existingProduct[ProductTable.id]
    } else {
        val result = createUpdateProduct(CreateUpdateProductParams(params.barcode), user, testing)
        if (result.error != null) {
            return@transaction result
        }
        val newProduct = ProductTable.select { ProductTable.barcode eq params.barcode }.first()
        newProduct[ProductTable.id]
    }

    val now = now(params.testingNow, testing)
    val existingShop = ShopTable.select { ShopTable.osmUID eq osmUID.asStr }.firstOrNull()
    if (existingShop != null && existingShop[ShopTable.deleted]) {
        return@transaction GenericResponse.failure("shop_deleted", "OSM UID: $osmUID")
    }

    val shopId = if (existingShop != null) {
        ShopTable.maybeValidate(
            Shop.from(existingShop),
            user,
            now,
            freshLat = params.lat,
            freshLon = params.lon)
        existingShop[ShopTable.id]
    } else {
        val inserted = ShopTable.insertWithValidation(
            ShopValidationReason.NEVER_VALIDATED_BEFORE,
            user.id,
            osmUID,
            now,
            params.lat,
            params.lon,
        )
        insertNewsPiece(params, user, now, osmUID)
        inserted[ShopTable.id]
    }

    val existingRow = ProductAtShopTable.select {
        (ProductAtShopTable.productId eq productId) and (ProductAtShopTable.shopId eq shopId)
    }.firstOrNull()
    if (existingRow != null) {
        // Already done!
        return@transaction GenericResponse.success()
    }

    // Insert product
    ProductAtShopTable.insert {
        it[ProductAtShopTable.productId] = productId
        it[ProductAtShopTable.shopId] = shopId
        it[sourceCode] = source.persistentCode
        it[creationTime] = now
        it[creatorUserId] = user.id
    }
    // Increase products count
    val productsCountValue = ProductAtShopTable.countAcceptableProducts(shopId)
    ShopTable.update( { ShopTable.id eq shopId } ) {
        it[productsCount] = productsCountValue.toInt()
    }

    productPresenceVote(
        ProductPresenceVoteParams(params.barcode, null, osmUID.asStr, 1, params.testingNow),
        user,
        testing)

    UserContributionTable.add(
        user,
        UserContributionType.PRODUCT_ADDED_TO_SHOP,
        now,
        barcode = params.barcode,
        shopUID = osmUID)

    GenericResponse.success()
}

private fun insertNewsPiece(
    params: PutProductToShopParams,
    user: User,
    now: Long,
    osmUID: OsmUID,
) {
    if (params.lat != null && params.lon != null) {
        val insertedNewsPiece = NewsPieceTable.insert {
            it[lat] = params.lat
            it[lon] = params.lon
            it[creatorUserId] = user.id
            it[creationTime] = now
            it[type] = NewsPieceType.PRODUCT_AT_SHOP.persistentCode
        }
        val newsPieceId = insertedNewsPiece[NewsPieceTable.id]
        NewsPieceProductAtShopTable.insert {
            it[NewsPieceProductAtShopTable.newsPieceId] = newsPieceId
            it[barcode] = params.barcode
            it[shopUID] = osmUID.asStr
        }
    }
}
