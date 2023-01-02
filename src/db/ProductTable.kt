package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.or
import vegancheckteam.plante_server.model.VegStatus

object ProductTable : Table("product") {
    val id = integer("id").autoIncrement()
    val barcode = text("barcode").uniqueIndex()
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).nullable().index()
    val veganStatus = short("vegan_status").nullable()
    val veganStatusSource = short("vegan_status_source").nullable()
    @Deprecated("Use 'moderatorVeganChoiceReasons'")
    val moderatorVeganChoiceReason = short("moderator_vegan_choice_reason").nullable()
    val moderatorVeganChoiceReasons =  text("moderator_vegan_choice_reasons").nullable()
    val moderatorVeganSourcesText = text("moderator_vegan_sources_text").nullable()
    override val primaryKey = PrimaryKey(id)

    val nothingNonVegan: Op<Boolean>
        get() = veganStatus.isNull() or (veganStatus neq VegStatus.NEGATIVE.persistentCode)

    fun select(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        // Intentionally nothing to do - we shadow an extension function with same name
    }

}
