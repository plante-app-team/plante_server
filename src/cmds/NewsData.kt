package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import java.util.concurrent.TimeUnit
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.AreaTooBigException
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.geoSelect
import vegancheckteam.plante_server.base.kmToGrad
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.NewsPieceProductAtShopTable
import vegancheckteam.plante_server.db.NewsPieceTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.db.deepDeleteNewsWhere
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
    val untilSecsUtc: Long? = null,
    val testingNow: Long? = null,
)

fun newsData(params: NewsDataParams, testing: Boolean): Any = transaction {
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

    val until = params.untilSecsUtc ?: now
    val withinTimeBounds = NewsPieceTable.creationTime lessEq until
    val userNotBanned = UserTable.banned eq false
    val result = NewsPiece.selectFromDB(
        where = withinBounds and withinTimeBounds and userNotBanned,
        pageSize = NEWS_PAGE_SIZE,
        pageNumber = params.page,
    )
    NewsDataResponse(
        news = result.take(NEWS_PAGE_SIZE),
        lastPage = result.size <= NEWS_PAGE_SIZE,
    )
}

private fun deleteOutdatedNews(now: Long) {
    val lastValidTime = now - TimeUnit.DAYS.toSeconds(NEWS_LIFETIME_DAYS)
    NewsPieceTable.deepDeleteNewsWhere {
        NewsPieceTable.creationTime less lastValidTime
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
