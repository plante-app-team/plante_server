package vegancheckteam.untitled_vegan_app_server

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.handle
import io.ktor.locations.location
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.util.pipeline.PipelineContext
import vegancheckteam.untitled_vegan_app_server.auth.userPrincipal
import vegancheckteam.untitled_vegan_app_server.model.GenericResponse
import vegancheckteam.untitled_vegan_app_server.model.User

@KtorExperimentalLocationsAPI
inline fun <reified T : Any> Route.getAuthed(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T, User) -> Unit
): Route {
    return location(T::class) {
        method(HttpMethod.Get) {
            handle<T> {
                val user = call.userPrincipal?.user
                validateUser(user)?.let { error ->
                    call.respond(error)
                    return@handle
                }
                body(it, user!!)
            }
        }
    }
}

fun validateUser(user: User?): GenericResponse? {
    if (user == null) {
        return GenericResponse.failure("invalid_token")
    }
    if (user.banned) {
        return GenericResponse.failure("banned")
    }
    return null
}
