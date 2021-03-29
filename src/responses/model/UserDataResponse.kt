package vegancheckteam.untitled_vegan_app_server.responses.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import vegancheckteam.untitled_vegan_app_server.GlobalStorage.jsonMapper
import vegancheckteam.untitled_vegan_app_server.model.User

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
    val eatsHoney: Boolean? = null) {

    companion object {
        fun from(user: User) = UserDataResponse(
                userId = user.id.toString(),
                name = user.name,
                gender = user.gender?.genderName,
                birthday = user.birthday,
                eatsMilk = user.eatsMilk,
                eatsEggs = user.eatsEggs,
                eatsHoney = user.eatsHoney)
    }
    override fun toString(): String = jsonMapper.writeValueAsString(this)
}
