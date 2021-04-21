package vegancheckteam.plante_server.responses

import io.ktor.locations.Location
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.responses.model.UserDataResponse

@Location("/user_data/")
data class UserDataParams(val unused: Int = 123)

fun userData(unused: UserDataParams, user: User) = UserDataResponse.from(user)
