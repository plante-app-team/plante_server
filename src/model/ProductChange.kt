package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage.jsonMapper
import vegancheckteam.plante_server.db.ProductChangeTable

data class ProductChange(
    @JsonProperty("barcode")
    val productBarcode: String,
    @JsonProperty("editor_id")
    val editorId: UUID,
    @JsonProperty("time")
    val time: Long,
    @JsonProperty("updated_product")
    val updatedProduct: Product) {

    companion object {
        fun from(tableRow: ResultRow) = ProductChange(
            productBarcode = tableRow[ProductChangeTable.productBarcode],
            editorId = tableRow[ProductChangeTable.editorId],
            time = tableRow[ProductChangeTable.time],
            updatedProduct = jsonMapper.readValue(tableRow[ProductChangeTable.newProductJson], Product::class.java))
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}

data class ProductsChangesList(
    @JsonProperty("changes")
    val changes: List<ProductChange>) {
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
