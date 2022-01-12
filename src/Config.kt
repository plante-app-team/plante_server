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

    @JsonProperty("osm_prod_user")
    val osmProdUser: String,
    @JsonProperty("osm_prod_password")
    val osmProdPassword: String,
    @JsonProperty("osm_testing_user")
    val osmTestingUser: String,
    @JsonProperty("osm_testing_password")
    val osmTestingPassword: String,

    @JsonProperty("off_prod_user")
    val offProdUser: String,
    @JsonProperty("off_prod_password")
    val offProdPassword: String,
    @JsonProperty("off_testing_user")
    val offTestingUser: String,
    @JsonProperty("off_testing_password")
    val offTestingPassword: String,

    @JsonProperty("aws_s3_access_key_id")
    val awsS3AccessKeyId: String,
    @JsonProperty("aws_s3_secret_access_key")
    val awsS3SecretAccessKey: String,
    @JsonProperty("aws_s3_region")
    val awsS3Region: String,
    @JsonProperty("aws_s3_bucket_name")
    val awsS3BucketName: String,

    @JsonProperty("jwt_secret")
    val jwtSecret: String,
    /**
     * Must be a completely random set of chars like:
     * zghcnbiqztgoeubhgecmhpilfvbdwlehquwasxjz
     * This is security by obscurity and therefore is not really nice.
     * But Ktor doesn't really give us a way to validate endpoint's caller,
     * so obscurity is the simplest thing we can do.
     * https://stackoverflow.com/questions/67054276/how-to-get-client-ip-with-ktor
     */
    @JsonProperty("metrics_endpoint")
    val metricsEndpoint: String,
    @JsonProperty("ios_backend_private_key_file_path")
    val iOSBackendPrivateKeyFilePath: String,
    @JsonProperty("allow_cors")
    val allowCors: Boolean = false,
    @JsonProperty("nominatim_enabled")
    val nominatimEnabled: Boolean = true,
    @JsonProperty("db_connection_attempts_timeout_seconds")
    val dbConnectionAttemptsTimeoutSeconds: Int,

    /**
     * For local WebAdmin development.
     */
    @JsonProperty("always_moderator_name")
    val alwaysModeratorName: String?,
) {
    companion object {
        lateinit var instance: Config
        var instanceInited = false

        fun initFromFile(path: String) {
            instance = fromStr(File(path).readText())
            instanceInited = true
        }

        fun fromStr(str: String): Config = jsonMapper.readValue(str, Config::class.java)
    }
}
