package vegancheckteam.plante_server.cmds

import io.ktor.locations.Location
import vegancheckteam.plante_server.model.User
import vegancheckteam.plante_server.model.UserDataResponse

@Location("/user_data/")
data class UserDataParams(val unused: Int = 123)

fun userData(user: User) = UserDataResponse.from(user)
