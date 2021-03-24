package vegancheckteam.untitled_vegan_app_server.auth

import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import vegancheckteam.untitled_vegan_app_server.model.User

data class UserPrincipal(val user: User) : Principal

val ApplicationCall.userPrincipal get(): UserPrincipal? =
    authentication.principal<UserPrincipal>()
