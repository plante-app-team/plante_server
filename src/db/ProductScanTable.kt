package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductScanTable : Table("product_scan") {
    val id = integer("id").autoIncrement()
    // NOTE: it doesn't reference a field in the Product table because
    // a scan doesn't require a product to exist in DB.
    val productBarcode = text("barcode").index()
    val userId = uuid("user_id").references(UserTable.id).index()
    val time = long("time").index()
    override val primaryKey = PrimaryKey(id)
}
