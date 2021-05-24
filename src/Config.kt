package vegancheckteam.plante_server

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.File
import vegancheckteam.plante_server.GlobalStorage.jsonMapper

data class Config(
    @JsonProperty("psql_url")
    val psqlUrl: String,
    @JsonProperty("psql_user")
    val psqlUser: String,
    @JsonProperty("psql_pass")
    val psqlPassword: String,
    @JsonProperty("db_connection_attempts_timeout_seconds")
    val dbConnectionAttemptsTimeoutSeconds: Int,
    @JsonProperty("jwt_secret")
    val jwtSecret: String,
    @JsonProperty("osm_prod_user")
    val osmProdUser: String,
    @JsonProperty("osm_prod_password")
    val osmProdPassword: String,
    @JsonProperty("osm_testing_user")
    val osmTestingUser: String,
    @JsonProperty("osm_testing_password")
    val osmTestingPassword: String,
    @JsonProperty("always_moderator_name")
    val alwaysModeratorName: String?,
    @JsonProperty("allow_cors")
    val allowCors: Boolean = false) {

    companion object {
        lateinit var instance: Config
        var instanceInited = false

        fun initFromFile(path: String) {
            instance = jsonMapper.readValue(File(path).readText(), Config::class.java)
            instanceInited = true
        }
    }
}
