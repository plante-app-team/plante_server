package vegancheckteam.plante_server.cmds

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.locations.Location
import java.util.Base64
import vegancheckteam.plante_server.Config
import vegancheckteam.plante_server.GlobalStorage
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserDataResponse

@Location("/mobile_app_config/")
data class MobileAppConfigDataParams(val globalConfigOverride: String? = null)

fun mobileAppConfigData(params: MobileAppConfigDataParams, user: User, testing: Boolean): Any {
    if (params.globalConfigOverride != null && !testing) {
        return GenericResponse.failure("denied")
    }
    val globalConfigOverrideStr = params.globalConfigOverride?.let { Base64.getDecoder().decode(it) }
    val globalConfigOverride = globalConfigOverrideStr?.let { Config.fromStr(String(it)) }
    val globalConfig = globalConfigOverride ?: Config.instance

    val userData = UserDataResponse.from(user)
    return MobileAppConfigDataResponse(userData, globalConfig.nominatimEnabled)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MobileAppConfigDataResponse(
    @JsonProperty("user_data")
    val userData: UserDataResponse,
    @JsonProperty("nominatim_enabled")
    val nominatimEnabled: Boolean) {
    override fun toString(): String = GlobalStorage.jsonMapper.writeValueAsString(this)
}
