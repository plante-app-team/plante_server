package vegancheckteam.plante_server.workers

import com.google.common.annotations.VisibleForTesting
import io.ktor.client.HttpClient
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.Shop
import vegancheckteam.plante_server.model.ShopValidationReason
import vegancheckteam.plante_server.osm.OpenStreetMap
import vegancheckteam.plante_server.osm.OsmShop

private const val MINUTE: Long = 1000 * 60

typealias OsmForTests = (uids: List<Shop>) -> Set<OsmShop>

object ShopsValidationWorker : BackgroundWorkerBase(
        name = "ShopsValidationWorker",
        backoffDelays = listOf(MINUTE, MINUTE * 2, MINUTE * 4, MINUTE * 10)) {
    const val SINGLE_VALIDATION_SHOPS_COUNT_MAX = 20
    private lateinit var httpClient: HttpClient

    private var testing = false
    var osmForTests: OsmForTests? = null

    fun start(httpClient: HttpClient, testing: Boolean) {
        this.httpClient = httpClient
        this.testing = testing
        scheduleValidationOfForgottenShops()
        super.start()
    }

    private fun scheduleValidationOfForgottenShops() = transaction {
        val invalidShop = (ShopTable.lastAutoValidationTime eq null) or
                (ShopTable.lat eq null) or
                (ShopTable.lon eq null)
        val notBeingManuallyModerated = (ModeratorTaskTable.taskType.isNull()) or (ModeratorTaskTable.taskType neq
                ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION.persistentCode)
        val autoValidationNotScheduled = notExists(ShopsValidationQueueTable.select {
            ShopsValidationQueueTable.shopId eq ShopTable.id
        })
        val notValidated = ShopTable.join(
                ModeratorTaskTable,
                joinType = JoinType.LEFT,
                onColumn = ShopTable.osmUID,
                otherColumn = ModeratorTaskTable.osmUID).select(
            invalidShop and autoValidationNotScheduled and notBeingManuallyModerated
        )

        for (row in notValidated) {
            val reason = if (row[ShopTable.lat] == null || row[ShopTable.lon] == null) {
                ShopValidationReason.COORDS_WERE_NULL
            } else if (row[ShopTable.lastAutoValidationTime] == null) {
                ShopValidationReason.NEVER_VALIDATED_BEFORE
            } else {
                throw Error("Proper ShopValidationReason could not be chosen")
            }

            Log.w("ShopsValidationWorker", "forgotten not-validated shop found: ${Shop.from(row)}")
            ShopsValidationQueueTable.insert {
                it[shopId] = row[ShopTable.id]
                it[enqueuingTime] = now(testing = testing)
                it[sourceUserId] = row[ShopTable.creatorUserId]
                it[ShopsValidationQueueTable.reason] = reason.persistentCode
            }
        }
    }

    /**
     * Schedules validation to the worker thread
     */
    fun scheduleValidation(shopId: Int, sourceUserId: UUID, reason: ShopValidationReason) {
        scheduleValidation(listOf(ShopsValidationWorkerTask(
            shopId, sourceUserId, reason
        )))
    }

    /**
     * Schedules validation to the worker thread
     */
    fun scheduleValidation(tasks: List<ShopsValidationWorkerTask>) {
        transaction {
            for (task in tasks) {
                ShopsValidationQueueTable.insert {
                    it[shopId] = task.shopId
                    it[enqueuingTime] = now(testing = testing)
                    it[sourceUserId] = task.sourceUserId
                    it[reason] = task.reason.persistentCode
                }
            }
            // We need to make sure the worker is awakened AFTER
            // the transaction is executed - otherwise it might
            // wake up, see no tasks, go to sleep, and only then the
            // task would appear in DB.
            afterTransactionCommit {
                wakeUp()
            }
        }
    }

    override fun hasWork(): Boolean = transaction {
        ShopsValidationQueueTable.selectAll().limit(1).count() > 0
    }

    override fun doWork() {
        Log.i("ShopsValidationWorker", "doWork start")
        val tasks = selectTasks()
        if (tasks.isEmpty()) {
            Log.i("ShopsValidationWorker", "doWork end - no work")
            return
        }
        val shops = tasks.map { it.shop }
        val osmShops = if (!testing) {
            runBlocking { OpenStreetMap.requestShopsFor(shops.map { it.osmUID }, httpClient) }
        } else if (osmForTests != null) {
            osmForTests!!.invoke(shops)
        } else {
            shops.map { OsmShop(it.osmUID, it.lat ?: 1.0, it.lon ?: 2.0) }.toSet()
        }
        Log.i("ShopsValidationWorker", "doWork tasks: $tasks, shops: $shops")
        validate(tasks, osmShops)
        Log.i("ShopsValidationWorker", "doWork end")
    }

    private fun selectTasks(): List<ShopValidationTask> = transaction {
        val result = mutableListOf<ShopValidationTask>()

        val topPrioritiesReasons = listOf(
            ShopValidationReason.NEVER_VALIDATED_BEFORE,
            ShopValidationReason.COORDS_WERE_NULL,
        )
        val otherReasons = ShopValidationReason.values().filter { !topPrioritiesReasons.contains(it) }
        val allReasons = topPrioritiesReasons + otherReasons

        for (reason in allReasons) {
            result += ShopsValidationQueueTable.innerJoin(ShopTable).select {
                ShopsValidationQueueTable.reason eq reason.persistentCode
            }.limit(SINGLE_VALIDATION_SHOPS_COUNT_MAX)
                .map { ShopValidationTask.from(Shop.from(it), it) }
            if (SINGLE_VALIDATION_SHOPS_COUNT_MAX <= result.size) {
                break
            }
        }
        result.take(SINGLE_VALIDATION_SHOPS_COUNT_MAX)
    }

    private fun validate(tasks: List<ShopValidationTask>, osmShops: Set<OsmShop>) = transaction {
        val osmShopsMap = osmShops.associateBy { it.uid }
        for (task in tasks) {
            val osmShop = osmShopsMap[task.shop.osmUID]
            if (osmShop != null) {
                ShopTable.update( { ShopTable.id eq task.shop.id } ) {
                    it[lat] = osmShop.lat
                    it[lon] = osmShop.lon
                    it[lastAutoValidationTime] = now(testing = testing)
                }
            } else {
                Log.w("ShopsValidationWorker", "shop for task is not found, creating moderator task. $task")
                ModeratorTaskTable.insert {
                    it[osmUID] = task.shop.osmUID.asStr
                    it[taskType] = ModeratorTaskType.OSM_SHOP_NEEDS_MANUAL_VALIDATION.persistentCode
                    it[taskSourceUserId] = task.sourceUserId
                    it[creationTime] = now(testing = testing)
                }
                // Auto-moderation created manual moderation task
                ShopTable.update( { ShopTable.id eq task.shop.id } ) {
                    it[lastAutoValidationTime] = now(testing = testing)
                }
            }
            ShopsValidationQueueTable.deleteWhere {
                ShopsValidationQueueTable.id eq task.id
            }
        }
    }
}

@VisibleForTesting
data class ShopsValidationWorkerTask(
    val shopId: Int,
    val sourceUserId: UUID,
    val reason: ShopValidationReason,
)

private data class ShopValidationTask(
    val id: Int,
    val shop: Shop,
    val enqueuingTime: Long,
    val sourceUserId: UUID,
    val reason: Short) {
    companion object {
        fun from(shop: Shop, tableRow: ResultRow): ShopValidationTask {
            return ShopValidationTask(
                id = tableRow[ShopsValidationQueueTable.id],
                shop = shop,
                enqueuingTime = tableRow[ShopsValidationQueueTable.enqueuingTime],
                sourceUserId = tableRow[ShopsValidationQueueTable.sourceUserId],
                reason = tableRow[ShopsValidationQueueTable.reason])
        }
    }
}

fun Transaction.afterTransactionCommit(fn: () -> Unit) {
    registerInterceptor(AfterCommitInterceptor(fn))
}

private class AfterCommitInterceptor(val fn: () -> Unit) : StatementInterceptor {
    override fun afterCommit() {
        fn.invoke()
    }
}
