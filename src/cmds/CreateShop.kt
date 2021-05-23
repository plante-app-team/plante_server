package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.locations.Location
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.base64.Base64
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

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
    val testingResponsesJsonBase64: String? = null)

suspend fun createShop(params: CreateShopParams, user: User, testing: Boolean, client: HttpClient): Any {
    val supportedShopTypes = ShopTypes.values().map { it.typeName }
    if (params.type !in supportedShopTypes) {
        return GenericResponse.failure("invalid_shop_type", "Supported types: $supportedShopTypes")
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
