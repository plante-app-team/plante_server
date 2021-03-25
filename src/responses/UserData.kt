package vegancheckteam.untitled_vegan_app_server.responses

import io.ktor.locations.Location
import vegancheckteam.untitled_vegan_app_server.model.User
import vegancheckteam.untitled_vegan_app_server.responses.model.UserDataResponse

@Location("/user_data/")
data class UserDataParams(val unused: Int = 123)

fun userData(unused: UserDataParams, user: User) = UserDataResponse.from(user)
