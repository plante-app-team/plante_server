package vegancheckteam.untitled_vegan_app_server

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

data class Config(
    @JsonProperty("psql_url")
    val psqlUrl: String,
    @JsonProperty("psql_user")
    val psqlUser: String,
    @JsonProperty("psql_pass")
    val psqlPassword: String,
    @JsonProperty("db_connection_attempts_timeout_seconds")
    val dbConnectionAttemptsTimeoutSeconds: Int) {

    companion object {
        fun fromFile(path: String): Config {
            val mapper = ObjectMapper()
            return mapper.readValue(File(path).readText(), Config::class.java)
        }
    }
}
