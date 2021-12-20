package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import vegancheckteam.plante_server.model.ProductAtShopSource

/**
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !Note that all insertions and deletions to this table MUST also change!
 * !value of ShopTable.productsCount!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
object ProductAtShopTable : Table("product_at_shop") {
    val id = integer("id").autoIncrement()
    val productId = integer("product_id").references(ProductTable.id).index()
    val shopId = integer("shop_id").references(ShopTable.id).index()
    val sourceCode = short("source").default(ProductAtShopSource.MANUAL.persistentCode)
    val creationTime = long("creation_time").index()
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).nullable().index()
    override val primaryKey = PrimaryKey(id)
    init {
        index(true, productId, shopId)
    }

    /**
     * All products except for ones with negative vegan status.
     */
    fun countAcceptableProducts(shopId: Int): Long {
        val idMatches = ProductAtShopTable.shopId eq shopId
        return innerJoin(ProductTable).select {
            idMatches and ProductTable.nothingNonVegan
        }.count()
    }
}
