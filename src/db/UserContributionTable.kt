package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserContributionType

object UserContributionTable : Table("user_contribution") {
    val id = integer("id").autoIncrement()
    val userId = uuid("user_id").references(UserTable.id).index()
    val barcode = text("barcode").nullable().index()
    val shopUID = text("shop_uid").nullable().index()
    val time = long("time").index()
    val type = short("type").index()

    fun add(user: User,
            type: UserContributionType,
            time: Long,
            barcode: String? = null,
            shopUID: OsmUID? = null,
            newsPieceID: Int? = null) {
        if (barcode == null && shopUID == null && newsPieceID == null) {
            throw IllegalArgumentException("Either barcode, shop UID or newsPieceID should not be null")
        }
        // NOTE: we don't store newsPieceID, because most likely it was reported, and very likely
        // it was deleted afterwards
        UserContributionTable.insert { row ->
            row[userId] = user.id
            row[UserContributionTable.barcode] = barcode
            row[UserContributionTable.shopUID] = shopUID?.asStr
            row[this.time] = time
            row[this.type] = type.persistentCode
        }
    }
}
