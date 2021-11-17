package vegancheckteam.plante_server

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.handle
import io.ktor.locations.location
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.pipeline.PipelineContext
import vegancheckteam.plante_server.auth.userPrincipal
import vegancheckteam.plante_server.model.GenericResponse
import vegancheckteam.plante_server.model.User

@KtorExperimentalLocationsAPI
inline fun <reified T : Any> Route.authedLocation(
    noinline body: suspend PipelineContext<Unit, ApplicationCall>.(T, User) -> Unit
): Route {
    return location(T::class) {
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

fun validateUser(user: User?): GenericResponse? {
    if (user == null) {
        return GenericResponse.failure("invalid_token")
    }
    if (user.banned) {
        return GenericResponse.failure("banned")
    }
    return null
}

fun Route.authedRoute(
        handle: suspend (call: ApplicationCall, user: User) -> Pair<HttpStatusCode, Any>,
) {
    handle {
        val user = call.userPrincipal?.user
        validateUser(user)?.let { error ->
            call.respond(error)
            return@handle
        }
        val (code, text) = handle(call, user!!)
        call.respond(code, text)
    }
}
