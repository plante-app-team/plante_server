package vegancheckteam.plante_server.workers

import com.google.common.annotations.VisibleForTesting
import io.ktor.client.HttpClient
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.notExists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import vegancheckteam.plante_server.base.Log
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
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
        val invalidRow = (ShopTable.lastValidationTime eq null) or
                (ShopTable.lat eq null) or
                (ShopTable.lon eq null)
        val notValidated = ShopTable.select {
            invalidRow and
                notExists(ShopsValidationQueueTable.select {
                    ShopsValidationQueueTable.shopId eq ShopTable.id
                })
        }

        for (row in notValidated) {
            val reason = if (row[ShopTable.lat] == null || row[ShopTable.lon] == null) {
                ShopValidationReason.COORDS_WERE_NULL
            } else if (row[ShopTable.lastValidationTime] == null) {
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
    fun scheduleValidation(shopId: Int, sourceUserId: UUID, reason: ShopValidationReason, now: Long) {
        scheduleValidation(listOf(ShopsValidationWorkerTask(
            shopId, sourceUserId, reason, now
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
                    it[enqueuingTime] = task.now
                    it[sourceUserId] = task.sourceUserId
                    it[reason] = task.reason.persistentCode
                }
            }
        }
        wakeUp()
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
            if (osmShop == null) {
                Log.w("ShopsValidationWorker", "shop for task is not found: $task")
                continue
            }
            val shopId = task.shop.id
            ShopTable.update( { ShopTable.id eq shopId } ) {
                it[lat] = osmShop.lat
                it[lon] = osmShop.lon
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
    val now: Long,
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
