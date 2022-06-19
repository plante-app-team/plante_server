package vegancheckteam.plante_server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import vegancheckteam.plante_server.GlobalStorage.jsonMapper
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.db.splitLangs

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserDataResponse(
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("client_token")
    val clientToken: String? = null,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("self_description")
    val selfDescription: String? = null,
    @JsonProperty("gender")
    val gender: String? = null,
    @JsonProperty("birthday")
    val birthday: String? = null,
    @JsonProperty("langs_prioritized")
    val langsPrioritized: List<String>? = null,
    @JsonProperty("rights_group")
    val rightsGroup: Short? = null,
    @JsonProperty("avatar_id")
    val avatarId: String? = null,
    @JsonProperty("google_id")
    val googleId: String? = null,
    @JsonProperty("apple_id")
    val appleId: String? = null,
) {

    companion object {
        fun from(user: User) = UserDataResponse(
                userId = user.id.toString(),
                name = user.name,
                selfDescription = user.selfDescription,
                gender = user.gender?.genderName,
                birthday = user.birthday,
                langsPrioritized = user.langsPrioritizedStr?.let { UserTable.splitLangs(it) },
                rightsGroup = user.userRightsGroup.persistentCode,
                avatarId = user.avatarId?.toString(),
                googleId = user.googleId,
                appleId = user.appleId,
        )
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
