package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductPresenceVoteTable : Table("product_presence_vote") {
    val id = integer("id").autoIncrement()
    val productId = integer("product_id").references(ProductTable.id).index()
    val shopId = integer("shop_id").references(ShopTable.id).index()
    val votedUserId = uuid("voted_user_id").references(UserTable.id).index()
    val voteTime = long("vote_time").index()
    val voteVal = short("vote_val")
    override val primaryKey = PrimaryKey(ProductPresenceVoteTable.id)
}
