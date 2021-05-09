package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductAtShopTable : Table("product_at_shop") {
    val id = integer("id").autoIncrement()
    val productId = integer("product_id").references(ProductTable.id).index()
    val shopId = integer("shop_id").references(ShopTable.id).index()
    val creationTime = long("creation_time")
    override val primaryKey = PrimaryKey(id)
    init {
        index(true, productId, shopId)
    }
}
