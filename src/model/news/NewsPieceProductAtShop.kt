package vegancheckteam.plante_server.model.news

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.model.OsmUID

data class NewsPieceProductAtShop(
    @JsonProperty("barcode")
    val barcode: String,
    @JsonProperty("shop_uid")
    val shopUID: OsmUID,
    @JsonIgnore
    override val newsPieceId: Int,
) : NewsPieceDataBase {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        fun from(tableRow: ResultRow): NewsPieceProductAtShop {
            val osmUID = OsmUID.from(tableRow[NewsPieceProductAtShopTable.shopUID])
            return NewsPieceProductAtShop(
                barcode = tableRow[NewsPieceProductAtShopTable.barcode],
                shopUID = osmUID,
                newsPieceId = tableRow[NewsPieceProductAtShopTable.newsPieceId]
            )
        }
    }
}
