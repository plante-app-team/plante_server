package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

/**
 * Must not be used anywhere outside ShopsValidationWorker.
 */
object ShopsValidationQueueTable : Table("shops_validation_queue") {
    val id = integer("id").autoIncrement()
    val shopId = integer("shop_id").references(ShopTable.id).index()
    val enqueuingTime = long("enqueuing_time").index()
    val sourceUserId = uuid("source_user_id").references(UserTable.id).index()
    val reason = short("reason").index()
    override val primaryKey = PrimaryKey(id)
}
