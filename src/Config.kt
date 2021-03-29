package vegancheckteam.untitled_vegan_app_server

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.File
import vegancheckteam.untitled_vegan_app_server.GlobalStorage.jsonMapper

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
    val jwtSecret: String) {

    companion object {
        lateinit var instance: Config
        var instanceInited = false

        fun initFromFile(path: String) {
            instance = jsonMapper.readValue(File(path).readText(), Config::class.java)
            instanceInited = true
        }
    }
}
