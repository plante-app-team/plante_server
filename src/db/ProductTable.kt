package vegancheckteam.plante_server.db

import org.jetbrains.exposed.sql.Table

object ProductTable : Table("product") {
    val id = integer("id").autoIncrement()
    val barcode = text("barcode").uniqueIndex()
    val creatorUserId = uuid("creator_user_id").references(UserTable.id).nullable().index()
    val vegetarianStatus = short("vegetarian_status").nullable()
    val veganStatus = short("vegan_status").nullable()
    val vegetarianStatusSource = short("vegetarian_status_source").nullable()
    val veganStatusSource = short("vegan_status_source").nullable()
    val moderatorVegetarianChoiceReason = short("moderator_vegetarian_choice_reason").nullable()
    val moderatorVegetarianSourcesText = text("moderator_vegetarian_sources_text").nullable()
    val moderatorVeganChoiceReason = short("moderator_vegan_choice_reason").nullable()
    val moderatorVeganSourcesText = text("moderator_vegan_sources_text").nullable()
    override val primaryKey = PrimaryKey(id)
}
