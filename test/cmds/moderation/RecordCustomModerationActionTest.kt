package vegancheckteam.plante_server.cmds.moderation

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.server.testing.TestApplicationEngine
import java.util.UUID
import kotlin.test.assertEquals
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Before
import org.junit.Test
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.OsmUID
import vegancheckteam.plante_server.test_utils.authedGet
import vegancheckteam.plante_server.test_utils.jsonMap
import vegancheckteam.plante_server.test_utils.register
import vegancheckteam.plante_server.test_utils.registerModerator
import vegancheckteam.plante_server.test_utils.withPlanteTestApplication

class RecordCustomModerationActionTest {
    @Before
    fun setUp() {
        withPlanteTestApplication {
            transaction {
                ModeratorTaskTable.deleteAll()
            }
        }
    }

    @Test
    fun `record_custom_moderation_action normal scenario`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            var activities = getModeratorsActivities(moderator)
            assertEquals(0, activities.size, activities.toString())

            var now = 123L
            recordCustomModerationActionCmd(
                moderator,
                "action1",
                now = ++now)
            recordCustomModerationActionCmd(
                moderator,
                "action2",
                now = ++now)

            activities = getModeratorsActivities(moderator)
            assertEquals(2, activities.size, activities.toString())

            assertEquals("action2", activities[0]["resolver_action"], activities.toString())
            assertEquals(null, activities[0]["text_from_user"], activities.toString())
            assertEquals(now.toInt(), activities[0]["resolution_time"], activities.toString())
            assertEquals(now.toInt(), activities[0]["creation_time"], activities.toString())
            assertEquals(moderatorId.toString(), activities[0]["resolver"], activities.toString())
            assertEquals(moderatorId.toString(), activities[0]["task_source_user_id"], activities.toString())
            assertEquals("custom_moderation_action", activities[0]["task_type"], activities.toString())
            assertEquals(null, activities[0]["barcode"], activities.toString())
            assertEquals(null, activities[0]["osm_uid"], activities.toString())

            assertEquals("action1", activities[1]["resolver_action"], activities.toString())
            assertEquals(null, activities[1]["text_from_user"], activities.toString())
            assertEquals(now.toInt() - 1, activities[1]["resolution_time"], activities.toString())
            assertEquals(now.toInt() - 1, activities[1]["creation_time"], activities.toString())
            assertEquals(moderatorId.toString(), activities[1]["resolver"], activities.toString())
            assertEquals(moderatorId.toString(), activities[1]["task_source_user_id"], activities.toString())
            assertEquals("custom_moderation_action", activities[1]["task_type"], activities.toString())
            assertEquals(null, activities[1]["barcode"], activities.toString())
            assertEquals(null, activities[1]["osm_uid"], activities.toString())
        }
    }

    @Test
    fun `record_custom_moderation_action with barcode`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            var activities = getModeratorsActivities(moderator)
            assertEquals(0, activities.size, activities.toString())

            var now = 123L
            recordCustomModerationActionCmd(
                moderator,
                "cool action",
                barcode = "123",
                now = ++now)

            activities = getModeratorsActivities(moderator)
            assertEquals(1, activities.size, activities.toString())

            assertEquals("cool action", activities[0]["resolver_action"], activities.toString())
            assertEquals("123", activities[0]["barcode"], activities.toString())
            assertEquals(null, activities[0]["osm_uid"], activities.toString())
        }
    }

    @Test
    fun `record_custom_moderation_action with osm UID`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            var activities = getModeratorsActivities(moderator)
            assertEquals(0, activities.size, activities.toString())

            var now = 123L
            recordCustomModerationActionCmd(
                moderator,
                "cool action",
                osmUID = "1:123",
                now = ++now)

            activities = getModeratorsActivities(moderator)
            assertEquals(1, activities.size, activities.toString())

            assertEquals("cool action", activities[0]["resolver_action"], activities.toString())
            assertEquals(null, activities[0]["barcode"], activities.toString())
            assertEquals("1:123", activities[0]["osm_uid"], activities.toString())
        }
    }

    @Test
    fun `record_custom_moderation_action with barcode and osm UID`() {
        withPlanteTestApplication {
            val moderatorId = UUID.randomUUID()
            val moderator = registerModerator(moderatorId)
            var activities = getModeratorsActivities(moderator)
            assertEquals(0, activities.size, activities.toString())

            var now = 123L
            recordCustomModerationActionCmd(
                moderator,
                "cool action",
                barcode = "123",
                osmUID = "1:123",
                now = ++now)

            activities = getModeratorsActivities(moderator)
            assertEquals(1, activities.size, activities.toString())

            assertEquals("cool action", activities[0]["resolver_action"], activities.toString())
            assertEquals("123", activities[0]["barcode"], activities.toString())
            assertEquals("1:123", activities[0]["osm_uid"], activities.toString())
        }
    }

    @Test
    fun `record_custom_moderation_action cannot be touched by normal user`() {
        withPlanteTestApplication {
            val user = register()
            val map = authedGet(user, "/record_custom_moderation_action/", mapOf(
                "performedAction" to "action"
            )).jsonMap()
            assertEquals("denied", map["error"])
        }
    }

    private fun TestApplicationEngine.getModeratorsActivities(
        moderator: String,
        since: Int = 0): List<Map<*, *>> {
        val map = authedGet(moderator, "/moderators_activities/?since=$since").jsonMap()
        return (map["result"] as List<*>).map { it as Map<*, *> }
    }

    private fun TestApplicationEngine.recordCustomModerationActionCmd(
            moderator: String,
            performedAction: String,
            barcode: String? = null,
            osmUID: String? = null,
            now: Long = now()) {
        val args = mutableMapOf<String, String>()
        args["performedAction"] = performedAction
        if (barcode != null) {
            args["barcode"] = barcode
        }
        if (osmUID != null) {
            args["osmUID"] = osmUID
        }
        args["testingNow"] = now.toString()

        val map = authedGet(moderator, "/record_custom_moderation_action/", args).jsonMap()
        assertEquals("ok", map["result"])
    }
}