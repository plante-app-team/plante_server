package vegancheckteam.plante_server.db

import java.util.UUID
import kotlin.math.abs
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.ShopValidationReason
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.workers.ShopsValidationWorker

object ShopTable : Table("shop") {
    const val MIN_ALLOWED_SHOP_MOVE_DISTANCE = 0.000001

    val id = integer("id").autoIncrement()
    /**
     * PLEASE NOTE: this ID is not the same thing as the ID in Open Street Map.
     * is a combination of multiple OSM elements fields to make
     * the ID of an [osmUID] unique even among multiple OSM elements types.
     * Hence, "UID" - Unique IDentifier.
     */
    // NOTE: column name is deprecated -
    // should be renamed to "osm_uid", but it's not easy to do with Exposed
    val osmUID = text("osm_id").uniqueIndex()
    val creationTime = long("creation_time").index()
    val createdNewOsmNode = bool("created_new_osm_node").default(false)
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).index()
    val productsCount = integer("products_count").default(0)
    val lat = double("lat").nullable().index()
    val lon = double("lon").nullable().index()
    val lastAutoValidationTime = long("last_auto_validation_time").nullable().index()
    val deleted = bool("deleted").default(false)
    override val primaryKey = PrimaryKey(id)

    fun insertWithValidation(
            reason: ShopValidationReason,
            creator: UUID,
            osmUID: OsmUID,
            creationTime: Long,
            lat: Double?,
            lon: Double?,
            createdNewOsmNode: Boolean = false): InsertStatement<Number> {
        val result = insert {
            it[ShopTable.osmUID] = osmUID.asStr
            it[ShopTable.creationTime] = creationTime
            it[creatorUserId] = creator
            it[ShopTable.lat] = lat
            it[ShopTable.lon] = lon
            it[ShopTable.createdNewOsmNode] = createdNewOsmNode
        }
        ShopsValidationWorker.scheduleValidation(
            result[id], creator, reason
        )
        return result
    }

    fun insertValidated(
            creator: UUID,
            osmUID: OsmUID,
            creationTime: Long,
            validationTime: Long,
            lat: Double?,
            lon: Double?,
            createdNewOsmNode: Boolean = false): InsertStatement<Number> {
        return insert {
            it[ShopTable.osmUID] = osmUID.asStr
            it[ShopTable.creationTime] = creationTime
            it[creatorUserId] = creator
            it[ShopTable.lat] = lat
            it[ShopTable.lon] = lon
            it[ShopTable.createdNewOsmNode] = createdNewOsmNode
            it[lastAutoValidationTime] = validationTime
        }
    }

    fun maybeValidate(shop: Shop, user: User, now: Long, freshLat: Double?, freshLon: Double?) {
        if (freshLat != null
                && freshLon != null
                && shop.lat != null
                && shop.lon != null) {
            if (MIN_ALLOWED_SHOP_MOVE_DISTANCE <= abs(freshLat - shop.lat)
                    || MIN_ALLOWED_SHOP_MOVE_DISTANCE <= abs(freshLon - shop.lon)) {
                ShopsValidationWorker.scheduleValidation(
                    shop.id, user.id, ShopValidationReason.SHOP_MOVED
                )
            }
        }
    }
}
