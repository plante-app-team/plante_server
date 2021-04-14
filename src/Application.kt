package vegancheckteam.untitled_vegan_app_server

import com.fasterxml.jackson.databind.*
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.logging.*
import io.ktor.features.*
import io.ktor.jackson.*
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.*
import vegancheckteam.untitled_vegan_app_server.auth.CioHttpTransport
import vegancheckteam.untitled_vegan_app_server.auth.JwtController
import vegancheckteam.untitled_vegan_app_server.db.ModeratorTaskTable
import vegancheckteam.untitled_vegan_app_server.db.ProductChangeTable
import vegancheckteam.untitled_vegan_app_server.db.ProductScanTable
import vegancheckteam.untitled_vegan_app_server.db.ProductTable
import vegancheckteam.untitled_vegan_app_server.db.UserQuizTable
import vegancheckteam.untitled_vegan_app_server.db.UserTable
import vegancheckteam.untitled_vegan_app_server.responses.BanMeParams
import vegancheckteam.untitled_vegan_app_server.responses.CreateUpdateProductParams
import vegancheckteam.untitled_vegan_app_server.responses.LoginParams
import vegancheckteam.untitled_vegan_app_server.responses.MakeReportParams
import vegancheckteam.untitled_vegan_app_server.responses.ProductChangesDataParams
import vegancheckteam.untitled_vegan_app_server.responses.ProductDataParams
import vegancheckteam.untitled_vegan_app_server.responses.ProductScanParams
import vegancheckteam.untitled_vegan_app_server.responses.RegisterParams
import vegancheckteam.untitled_vegan_app_server.responses.SignOutAllParams
import vegancheckteam.untitled_vegan_app_server.responses.UpdateUserDataParams
import vegancheckteam.untitled_vegan_app_server.responses.UserDataParams
import vegancheckteam.untitled_vegan_app_server.responses.UserQuizDataParams
import vegancheckteam.untitled_vegan_app_server.responses.UserQuizParams
import vegancheckteam.untitled_vegan_app_server.responses.banMe
import vegancheckteam.untitled_vegan_app_server.responses.createUpdateProduct
import vegancheckteam.untitled_vegan_app_server.responses.loginUser
import vegancheckteam.untitled_vegan_app_server.responses.makeReport
import vegancheckteam.untitled_vegan_app_server.responses.productChangesData
import vegancheckteam.untitled_vegan_app_server.responses.productData
import vegancheckteam.untitled_vegan_app_server.responses.productScan
import vegancheckteam.untitled_vegan_app_server.responses.registerUser
import vegancheckteam.untitled_vegan_app_server.responses.signOutAll
import vegancheckteam.untitled_vegan_app_server.responses.updateUserData
import vegancheckteam.untitled_vegan_app_server.responses.userData
import vegancheckteam.untitled_vegan_app_server.responses.userQuiz
import vegancheckteam.untitled_vegan_app_server.responses.userQuizData

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
            // WARNING: beware of JWT changes - any change can lead to all tokens invalidation
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
            getAuthed<BanMeParams> { _, user ->
                if (!testing) {
                    return@getAuthed
                }
                call.respond(banMe(user))
            }
            getAuthed<UserDataParams> { params, user ->
                call.respond(userData(params, user))
            }
            getAuthed<UpdateUserDataParams> { params, user ->
                call.respond(updateUserData(params, user))
            }
            getAuthed<SignOutAllParams> { params, user ->
                call.respond(signOutAll(params, user))
            }
            getAuthed<CreateUpdateProductParams> { params, user ->
                call.respond(createUpdateProduct(params, user))
            }
            getAuthed<ProductDataParams> { params, user ->
                call.respond(productData(params, user))
            }
            getAuthed<ProductChangesDataParams> { params, user ->
                call.respond(productChangesData(params, user))
            }
            getAuthed<UserQuizParams> { params, user ->
                call.respond(userQuiz(params, user))
            }
            getAuthed<UserQuizDataParams> { _, user ->
                call.respond(userQuizData(user))
            }
            getAuthed<MakeReportParams> { params, user ->
                call.respond(makeReport(params, user, testing))
            }
            getAuthed<ProductScanParams> { params, user ->
                call.respond(productScan(params, user, testing))
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
        SchemaUtils.createMissingTablesAndColumns(
            UserTable,
            ProductTable,
            ProductChangeTable,
            UserQuizTable,
            ModeratorTaskTable,
            ProductScanTable)
    }
}
