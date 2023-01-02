package vegancheckteam.plante_server.db

import java.util.*
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.model.Product
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.VegStatus
import vegancheckteam.plante_server.model.VegStatusSource

private val tableAliasLikesCount = ProductLikeTable.alias("likesCountAlias")
private val tableAliasMyLikesCount = ProductLikeTable.alias("myLikesAlias")
private val columnAliasLikesCount = tableAliasLikesCount[ProductLikeTable.id].count().alias("AllLikesCount")
private val columnAliasMyLikesCount = tableAliasMyLikesCount[ProductLikeTable.id].count().alias("MyLikesCount")

fun ProductTable.select2(by: User, joinWith: List<Table> = emptyList(), where: () -> Op<Boolean>) = select2(by.id, joinWith, where)

fun ProductTable.select2(by: UUID, joinWith: List<Table> = emptyList(), where: () -> Op<Boolean>): Query {
    val allColumns = ProductTable.columns + joinWith.flatMap { it.columns }
    return ProductTable
        .let {
            var joined = Join(it)
            for (table in joinWith) {
                joined = joined.leftJoin(table)
            }
            joined
        }
        .leftJoin(tableAliasLikesCount, { ProductTable.barcode }, { tableAliasLikesCount[ProductLikeTable.barcode] })
        .leftJoin(
            tableAliasMyLikesCount,
            { ProductTable.barcode },
            { tableAliasMyLikesCount[ProductLikeTable.barcode] },
            { tableAliasMyLikesCount[ProductLikeTable.userId] eq by })
        .slice(
            columnAliasLikesCount,
            columnAliasMyLikesCount,
            *allColumns.toTypedArray(),
        )
        .select(where.invoke())
        .groupBy(ProductTable.id)
        .let {
            var result = it
            for (table in joinWith) {
                result = result.groupBy(table.primaryKey!!.columns.first())
            }
            result
        }
}

fun Product.Companion.from(tableRow: ResultRow): Product {
    val moderatorVeganChoiceReasons = tableRow[ProductTable.moderatorVeganChoiceReasons]
    val moderatorVeganChoiceReason: Short?
    if (moderatorVeganChoiceReasons.isNullOrBlank()) {
        moderatorVeganChoiceReason = null
    } else {
        moderatorVeganChoiceReason = moderatorVeganChoiceReasons
            .split(Product.MODERATOR_CHOICE_REASON_SEPARATOR)
            .firstOrNull()
            ?.toShortOrNull()
    }
    return Product(
        id = tableRow[ProductTable.id],
        barcode = tableRow[ProductTable.barcode],
        veganStatus = vegStatusFrom(tableRow[ProductTable.veganStatus]),
        veganStatusSource = vegStatusSourceFrom(tableRow[ProductTable.veganStatusSource]),
        moderatorVeganChoiceReason = moderatorVeganChoiceReason,
        moderatorVeganChoiceReasons = tableRow[ProductTable.moderatorVeganChoiceReasons],
        moderatorVeganSourcesText = tableRow[ProductTable.moderatorVeganSourcesText],
        likedByMe = tableRow[columnAliasMyLikesCount] > 0,
        likesCount = tableRow[columnAliasLikesCount],
    )
}

private fun vegStatusFrom(code: Short?) = code?.let { VegStatus.fromPersistentCode(it) }
private fun vegStatusSourceFrom(code: Short?) = code?.let { VegStatusSource.fromPersistentCode(it) }
