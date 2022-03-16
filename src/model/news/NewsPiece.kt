package vegancheckteam.plante_server.model.news

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.db.NewsPieceTable

data class NewsPiece(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("lat")
    val lat: Double,
    @JsonProperty("lon")
    val lon: Double,
    @JsonProperty("creator_user_id")
    val creatorUserId: UUID,
    @JsonProperty("creation_time")
    val creationTime: Long,
    @JsonProperty("type")
    val type: Short,
    @JsonProperty("data")
    val data: Map<*, *>,
) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        fun from(tableRow: ResultRow): NewsPiece {
            return NewsPiece(
                id = tableRow[NewsPieceTable.id],
                lat = tableRow[NewsPieceTable.lat],
                lon = tableRow[NewsPieceTable.lon],
                creatorUserId = tableRow[NewsPieceTable.creatorUserId],
                creationTime = tableRow[NewsPieceTable.creationTime],
                type = tableRow[NewsPieceTable.type],
                data = emptyMap<Any, Any>(),
            )
        }
    }
}
