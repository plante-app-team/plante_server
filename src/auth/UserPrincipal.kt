package vegancheckteam.plante_server.auth

import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import vegancheckteam.plante_server.model.User

data class UserPrincipal(val user: User) : Principal

val ApplicationCall.userPrincipal get(): UserPrincipal? =
    authentication.principal<UserPrincipal>()
