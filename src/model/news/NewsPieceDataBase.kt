package vegancheckteam.plante_server.model.news

import vegancheckteam.plante_server.GlobalStorage

interface NewsPieceDataBase {
    val newsPieceId: Int
    fun toData(): Map<*, *> = GlobalStorage.jsonMapper.convertValue(this, Map::class.java)
}
