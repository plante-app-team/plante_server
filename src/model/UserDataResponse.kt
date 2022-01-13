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
    @JsonProperty("gender")
    val gender: String? = null,
    @JsonProperty("birthday")
    val birthday: String? = null,
    @JsonProperty("eats_milk")
    val eatsMilk: Boolean? = null,
    @JsonProperty("eats_eggs")
    val eatsEggs: Boolean? = null,
    @JsonProperty("eats_honey")
    val eatsHoney: Boolean? = null,
    @JsonProperty("langs_prioritized")
    val langsPrioritized: List<String>? = null,
    @JsonProperty("rights_group")
    val rightsGroup: Short? = null,
    @JsonProperty("has_avatar")
    val hasAvatar: Boolean? = null) {

    companion object {
        fun from(user: User) = UserDataResponse(
                userId = user.id.toString(),
                name = user.name,
                gender = user.gender?.genderName,
                birthday = user.birthday,
                eatsMilk = user.eatsMilk,
                eatsEggs = user.eatsEggs,
                eatsHoney = user.eatsHoney,
                langsPrioritized = user.langsPrioritizedStr?.let { UserTable.splitLangs(it) },
                rightsGroup = user.userRightsGroup.persistentCode,
                hasAvatar = user.hasAvatar)
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
