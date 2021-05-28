package vegancheckteam.plante_server

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logging
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.path
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.options
import io.ktor.routing.routing
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import vegancheckteam.plante_server.auth.CioHttpTransport
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.cmds.AllModeratorTasksDataParams
import vegancheckteam.plante_server.cmds.AssignModeratorTaskParams
import vegancheckteam.plante_server.cmds.AssignedModeratorTasksDataParams
import vegancheckteam.plante_server.cmds.BanMeParams
import vegancheckteam.plante_server.cmds.CreateShopParams
import vegancheckteam.plante_server.cmds.CreateUpdateProductParams
import vegancheckteam.plante_server.cmds.DeleteUserParams
import vegancheckteam.plante_server.cmds.LoginParams
import vegancheckteam.plante_server.cmds.MakeReportParams
import vegancheckteam.plante_server.cmds.ModerateProductVegStatusesParams
import vegancheckteam.plante_server.cmds.ProductChangesDataParams
import vegancheckteam.plante_server.cmds.ProductDataParams
import vegancheckteam.plante_server.cmds.ProductPresenceVoteParams
import vegancheckteam.plante_server.cmds.ProductPresenceVotesDataParams
import vegancheckteam.plante_server.cmds.ProductScanParams
import vegancheckteam.plante_server.cmds.ProductsAtShopsDataParams
import vegancheckteam.plante_server.cmds.PutProductToShopParams
import vegancheckteam.plante_server.cmds.RegisterParams
import vegancheckteam.plante_server.cmds.ResolveModeratorTaskParams
import vegancheckteam.plante_server.cmds.ShopsDataParams
import vegancheckteam.plante_server.cmds.ShopsDataRequestBody
import vegancheckteam.plante_server.cmds.SignOutAllParams
import vegancheckteam.plante_server.cmds.UnresolveModeratorTaskParams
import vegancheckteam.plante_server.cmds.UpdateUserDataParams
import vegancheckteam.plante_server.cmds.UserDataParams
import vegancheckteam.plante_server.cmds.UserQuizDataParams
import vegancheckteam.plante_server.cmds.UserQuizParams
import vegancheckteam.plante_server.cmds.allModeratorTasksData
import vegancheckteam.plante_server.cmds.assignModeratorTask
import vegancheckteam.plante_server.cmds.assignedModeratorTasksData
import vegancheckteam.plante_server.cmds.banMe
import vegancheckteam.plante_server.cmds.createShop
import vegancheckteam.plante_server.cmds.createUpdateProduct
import vegancheckteam.plante_server.cmds.deleteUser
import vegancheckteam.plante_server.cmds.loginUser
import vegancheckteam.plante_server.cmds.makeReport
import vegancheckteam.plante_server.cmds.moderateProductVegStatuses
import vegancheckteam.plante_server.cmds.productChangesData
import vegancheckteam.plante_server.cmds.productData
import vegancheckteam.plante_server.cmds.productPresenceVote
import vegancheckteam.plante_server.cmds.productPresenceVotesData
import vegancheckteam.plante_server.cmds.productScan
import vegancheckteam.plante_server.cmds.productsAtShopsData
import vegancheckteam.plante_server.cmds.putProductToShop
import vegancheckteam.plante_server.cmds.registerUser
import vegancheckteam.plante_server.cmds.resolveModeratorTask
import vegancheckteam.plante_server.cmds.shopsData
import vegancheckteam.plante_server.cmds.signOutAll
import vegancheckteam.plante_server.cmds.unresolveModeratorTask
import vegancheckteam.plante_server.cmds.updateUserData
import vegancheckteam.plante_server.cmds.userData
import vegancheckteam.plante_server.cmds.userQuiz
import vegancheckteam.plante_server.cmds.userQuizData
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductChangeTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductScanTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.UserQuizTable
import vegancheckteam.plante_server.db.UserTable

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
            realm = "vegancheckteam.plante server"
            verifier(JwtController.verifier.value)
            validate { JwtController.principalFromCredential(it) }
        }
    }

    if (Config.instance.allowCors) {
        install(CORS) {
            method(HttpMethod.Options)
            header(HttpHeaders.Authorization)
            header(HttpHeaders.ContentType)
            allowCredentials = true
            allowNonSimpleContentTypes = true
            anyHost()
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
            getAuthed<AssignModeratorTaskParams> { params, user ->
                call.respond(assignModeratorTask(params, user, testing))
            }
            getAuthed<AssignedModeratorTasksDataParams> { params, user ->
                call.respond(assignedModeratorTasksData(params, user, testing))
            }
            getAuthed<AllModeratorTasksDataParams> { params, user ->
                call.respond(allModeratorTasksData(params, user, testing))
            }
            getAuthed<ResolveModeratorTaskParams> { params, user ->
                call.respond(resolveModeratorTask(params, user, testing))
            }
            getAuthed<UnresolveModeratorTaskParams> { params, user ->
                call.respond(unresolveModeratorTask(params, user, testing))
            }
            getAuthed<ModerateProductVegStatusesParams> { params, user ->
                call.respond(moderateProductVegStatuses(params, user))
            }
            getAuthed<DeleteUserParams> { params, user ->
                call.respond(deleteUser(params, user))
            }
            getAuthed<PutProductToShopParams> { params, user ->
                call.respond(putProductToShop(params, user, testing))
            }
            getAuthed<ProductsAtShopsDataParams> { params, user ->
                call.respond(productsAtShopsData(params, user))
            }
            getAuthed<ProductPresenceVoteParams> { params, user ->
                call.respond(productPresenceVote(params, user, testing))
            }
            getAuthed<ProductPresenceVotesDataParams> { params, user ->
                call.respond(productPresenceVotesData(params, user))
            }
            getAuthed<CreateShopParams> { params, user ->
                call.respond(createShop(params, user, testing, client))
            }
            getAuthed<ShopsDataParams> { params, user ->
                val body = call.receive<ShopsDataRequestBody>()
                call.respond(shopsData(params, body, user))
            }
        }
    }
}

private fun mainServerInit() {
    if (!Config.instanceInited) {
        val path = System.getenv("PLANTE_BACKEND_CONFIG_FILE_PATH")
        if (path == null) {
            throw IllegalStateException("Please provide either --config-path or PLANTE_BACKEND_CONFIG_FILE_PATH env variable")
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
            ProductScanTable,
            ShopTable,
            ProductAtShopTable,
            ProductPresenceVoteTable,
        )
    }
}
