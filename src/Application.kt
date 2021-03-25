package vegancheckteam.untitled_vegan_app_server

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import com.fasterxml.jackson.databind.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.jackson.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.logging.*
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.locations.get
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.auth.CioHttpTransport
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.auth.userPrincipal
import vegancheckteam.untitled_vegan_app_server.model.HttpResponse
import vegancheckteam.untitled_vegan_app_server.responses.LoginParams
import vegancheckteam.untitled_vegan_app_server.responses.RegisterParams
import vegancheckteam.untitled_vegan_app_server.responses.SignOutAllParams
import vegancheckteam.untitled_vegan_app_server.responses.UpdateUserDataParams
import vegancheckteam.untitled_vegan_app_server.responses.UserDataParams
import vegancheckteam.untitled_vegan_app_server.responses.loginUser
import vegancheckteam.untitled_vegan_app_server.responses.registerUser
import vegancheckteam.untitled_vegan_app_server.responses.signOutAll
import vegancheckteam.untitled_vegan_app_server.responses.updateUserData
import vegancheckteam.untitled_vegan_app_server.responses.userData

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val configIndx = args.indexOf("--config-path")
        if (configIndx >= 0) {
            Config.initFromFile(args[configIndx+1])
        }
        io.ktor.server.cio.EngineMain.main(args)
    }
}

@KtorExperimentalLocationsAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    mainServerInit()

    install(Locations)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(Authentication) {
        jwt {
            realm = "vegancheckteam.untitled_vegan_app server"
            verifier(JwtController.verifier.value)
            validate { JwtController.principalFromCredential(it) }
        }
    }

    val client = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.HEADERS
        }
        install(HttpTimeout)
    }
    GlobalStorage.httpTransport = CioHttpTransport(client)


    routing {
        get<RegisterParams> { call.respond(registerUser(it, testing)) }
        get<LoginParams> { call.respond(loginUser(it, testing)) }

        authenticate {
            get<UserDataParams> {
                val user = call.userPrincipal?.user
                if (user == null) {
                    call.respond(HttpResponse.failure("invalid_token"))
                    return@get
                }
                if (user.banned) {
                    call.respond(HttpResponse.failure("banned"))
                    return@get
                }
                call.respond(userData(it, user))
            }
            get<UpdateUserDataParams> {
                val user = call.userPrincipal?.user
                if (user == null) {
                    call.respond(HttpResponse.failure("invalid_token"))
                    return@get
                }
                if (user.banned) {
                    call.respond(HttpResponse.failure("banned"))
                    return@get
                }
                call.respond(updateUserData(it, user))
            }
            get<SignOutAllParams> {
                val user = call.userPrincipal?.user
                if (user == null) {
                    call.respond(HttpResponse.failure("invalid_token"))
                    return@get
                }
                if (user.banned) {
                    call.respond(HttpResponse.failure("banned"))
                    return@get
                }
                call.respond(signOutAll(it, user))
            }
        }
    }
}

private fun mainServerInit() {
    if (!Config.instanceInited) {
        val path = System.getenv("CONFIG_FILE_PATH")
        if (path == null) {
            throw IllegalStateException("Please provide either --config-path or CONFIG_FILE_PATH env variable")
        }
        Config.initFromFile(path)
    }
    print("Provided config:\n${Config.instance}\n\n")

    Database.connect(
        "jdbc:${Config.instance.psqlUrl}",
        user = Config.instance.psqlUser,
        password = Config.instance.psqlPassword
    )
    transaction {
        SchemaUtils.createMissingTablesAndColumns(UserTable)
    }
}
