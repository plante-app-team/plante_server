package vegancheckteam.untitled_vegan_app_server.db

import org.jetbrains.exposed.sql.Table

object ProductChangeTable : Table("product_change") {
    val id = integer("id").autoIncrement()
    val productBarcode = text("barcode").references(ProductTable.barcode).index()
    val editorId = uuid("editor_id").references(UserTable.id).index()
    val oldProductJson = text("old_product_json")
    val newProductJson = text("new_product_json")
    val time = long("time")
    override val primaryKey = PrimaryKey(id)
}
