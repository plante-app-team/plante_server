package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import java.util.concurrent.TimeUnit
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.AreaTooBigException
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.geoSelect
import vegancheckteam.plante_server.base.kmToGrad
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.news.NewsPiece
import vegancheckteam.plante_server.model.news.NewsPieceDataBase
import vegancheckteam.plante_server.model.news.NewsPieceProductAtShop
import vegancheckteam.plante_server.model.news.NewsPieceType

const val NEWS_LIFETIME_DAYS = 90L
const val NEWS_MAX_SQUARE_SIZE_KMS = 40.0
const val NEWS_PAGE_SIZE = 20

@Location("/news_data/")
data class NewsDataParams(
    val west: Double,
    val east: Double,
    val north: Double,
    val south: Double,
    val page: Int,
    val testingNow: Long? = null,
)

fun newsData(params: NewsDataParams, user: User, testing: Boolean): Any = transaction {
    val now = now(params.testingNow, testing)
    deleteOutdatedNews(now)

    val withinBounds = try {
        geoSelect(
            params.west,
            params.east,
            params.north,
            params.south,
            NewsPieceTable.lat,
            NewsPieceTable.lon,
            maxSize = kmToGrad(NEWS_MAX_SQUARE_SIZE_KMS)
        )
    } catch (e: AreaTooBigException) {
        Log.w("/news_data/", "News from too big area are requested: $params")
        return@transaction GenericResponse.failure("area_too_big")
    }

    val pieces = NewsPieceTable
        .select(withinBounds)
        .orderBy(NewsPieceTable.creationTime, order = SortOrder.DESC)
        .limit(n = NEWS_PAGE_SIZE + 1, offset = params.page * NEWS_PAGE_SIZE.toLong())
        .map { NewsPiece.from(it) }

    val result = mutableListOf<NewsPiece>()
    for (newsType in NewsPieceType.values()) {
        val piecesWithType = pieces.filter { it.type == newsType.persistentCode }
        val piecesMap = piecesWithType.associateBy { it.id }
        val data = newsType.select(piecesWithType.map { it.id })
        for (dataEntry in data) {
            val piece = piecesMap[dataEntry.newsPieceId]
            if (piece == null) {
                Log.e("/news_data/", "Can't get news piece even though it must exist")
                continue
            }
            result.add(piece.copy(data = dataEntry.toData()))
        }
    }
    result.sortByDescending { it.creationTime }
    NewsDataResponse(
        news = result.take(NEWS_PAGE_SIZE),
        lastPage = result.size <= NEWS_PAGE_SIZE)
}

private fun deleteOutdatedNews(now: Long) {
    val lastValidTime = now - TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS)
    val whereOutdated = NewsPieceTable.creationTime less lastValidTime
    val outdatedNews = NewsPieceTable
        .select(whereOutdated)
        .map { NewsPiece.from(it) }
    for (newsType in NewsPieceType.values()) {
        val typedNews = outdatedNews.filter { it.type == newsType.persistentCode }
        newsType.deleteWhereParentsAre(typedNews.map { it.id })
    }
    NewsPieceTable.deleteWhere { whereOutdated }
}

fun NewsPieceType.select(ids: List<Int>): List<NewsPieceDataBase> {
    return when (this) {
        NewsPieceType.PRODUCT_AT_SHOP -> NewsPieceProductAtShopTable.select(
            NewsPieceProductAtShopTable.newsPieceId inList ids)
            .map { NewsPieceProductAtShop.from(it) }
    }
}

fun NewsPieceType.deleteWhereParentsAre(ids: List<Int>) {
    when (this) {
        NewsPieceType.PRODUCT_AT_SHOP -> NewsPieceProductAtShopTable.deleteWhere {
            NewsPieceProductAtShopTable.newsPieceId inList ids
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NewsDataResponse(
    @JsonProperty("results")
    val news: List<NewsPiece>,
    @JsonProperty("last_page")
    val lastPage: Boolean,
) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
