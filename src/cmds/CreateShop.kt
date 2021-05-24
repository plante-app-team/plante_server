package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.locations.Location
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.base64.Base64
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.base.now
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.ModeratorTaskType
import vegancheckteam.plante_server.model.User

const val MAX_CREATED_SHOPS_IN_SEQUENCE = 10
const val SHOPS_CREATION_SEQUENCE_LENGTH_SECS = 60 * 60 * 24 // a day

enum class ShopTypes(val typeName: String) {
    SUPERMARKET("supermarket"),
    CONVENIENCE("convenience"),
    GROCERY("grocery"),
    GREENGROCER("greengrocer"),
    GENERAL("general");
    companion object {
        fun fromStringType(str: String) = values().find { it.typeName == str.toLowerCase() }
    }
}

@Location("/create_shop/")
data class CreateShopParams(
    val lat: Double,
    val lon: Double,
    val name: String,
    val type: String,
    val productionDb: Boolean = true,
    val testingResponsesJsonBase64: String? = null,
    val testingNow: Long? = null)

suspend fun createShop(params: CreateShopParams, user: User, testing: Boolean, client: HttpClient): Any {
    val supportedShopTypes = ShopTypes.values().map { it.typeName }
    if (params.type !in supportedShopTypes) {
        return GenericResponse.failure("invalid_shop_type", "Supported types: $supportedShopTypes")
    }

    val now = now(params.testingNow, testing)
    val shopsCreationSequenceSize = transaction {
         ShopTable.select {
             (ShopTable.creationTime greater (now - SHOPS_CREATION_SEQUENCE_LENGTH_SECS)) and
                     (ShopTable.createdNewOsmNode eq true)

        }.count()
    }
    if (shopsCreationSequenceSize >= MAX_CREATED_SHOPS_IN_SEQUENCE) {
        return GenericResponse.failure("max_shops_created_for_now", "Already created $shopsCreationSequenceSize shops")
    }

    val (osmUrl, osmUser, osmPass) = if (params.productionDb) {
        Triple("https://www.openstreetmap.org",
            Config.instance.osmProdUser,
            Config.instance.osmProdPassword)
    } else {
        Triple("https://master.apis.dev.openstreetmap.org",
            Config.instance.osmTestingUser,
            Config.instance.osmTestingPassword)

    }
    val osmCredentials = String(Base64.encode("$osmUser:$osmPass".toByteArray()))

    val testingResponse = if (testing && params.testingResponsesJsonBase64 != null) {
        val testingResponseJson = String(Base64.decode(params.testingResponsesJsonBase64))
        CreateShopTestingOsmResponses.from(testingResponseJson)
    } else {
        null
    }

    // Start OSM shop creation
    val osmChangesetId = if (testingResponse != null) {
        testingResponse.resp1
    } else {
        val resp = client.put<HttpResponse>("$osmUrl/api/0.6/changeset/create") {
            header("Authorization", "Basic $osmCredentials")
            body = """
                <osm>
                  <changeset>
                    <tag k="created_by" v="planteuser"/>
                    <tag k="comment" v="Organization creation by a Plante App user"/>
                  </changeset>
                </osm>
        """.trimIndent()
        }
        if (resp.status.value != 200) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            return GenericResponse.failure("osm_error")
        }
        resp.readText()
    }

    // Put a shop creation into the OSM changeset
    val osmShopId = if (testingResponse != null) {
        testingResponse.resp2
    } else {
        val resp = client.put<HttpResponse>("$osmUrl/api/0.6/node/create") {
            header("Authorization", "Basic $osmCredentials")
            body = """
                <osm>
                  <node changeset="$osmChangesetId" lat="${params.lat}" lon="${params.lon}">
                    <tag k="shop" v="${params.type}"/>
                    <tag k="name" v="${params.name}"/>
                  </node>
                </osm>
        """.trimIndent()
        }
        if (resp.status.value != 200) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            return GenericResponse.failure("osm_error")
        }
        resp.readText()
    }

    // Create a moderator task
    if (params.productionDb) {
        transaction {
            ShopTable.insert {
                it[osmId] = osmShopId
                it[creationTime] = now
                it[createdNewOsmNode] = true
                it[creatorUserId] = user.id
            }
            ModeratorTaskTable.insert {
                it[osmId] = osmShopId
                it[taskType] = ModeratorTaskType.OSM_SHOP_CREATION.persistentCode
                it[taskSourceUserId] = user.id
                it[creationTime] = now
            }
        }
    }

    if (testingResponse != null) {
        testingResponse.resp3
    } else {
        val resp = client.put<HttpResponse>("$osmUrl/api/0.6/changeset/$osmChangesetId/close") {
            header("Authorization", "Basic  $osmCredentials")
        }
        if (resp.status.value != 200) {
            // TODO(https://trello.com/c/XgGFE05M/): log error
            return GenericResponse.failure("osm_error")
        }
        resp.readText()
    }

    return CreateShopResponse(osmShopId);
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateShopTestingOsmResponses(
    @JsonProperty("resp1")
    val resp1: String,
    @JsonProperty("resp2")
    val resp2: String,
    @JsonProperty("resp3")
    val resp3: String) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
    companion object {
        fun from(jsonString: String): CreateShopTestingOsmResponses =
            GlobalStorage.jsonMapper.readValue(jsonString, CreateShopTestingOsmResponses::class.java)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateShopResponse(
    @JsonProperty("osm_id")
    val osmId: String) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
