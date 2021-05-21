package vegancheckteam.plante_server

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
import vegancheckteam.plante_server.auth.CioHttpTransport
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.db.ModeratorTaskTable
import vegancheckteam.plante_server.db.ProductAtShopTable
import vegancheckteam.plante_server.db.ProductChangeTable
import vegancheckteam.plante_server.db.ProductPresenceVoteTable
import vegancheckteam.plante_server.db.ProductScanTable
import vegancheckteam.plante_server.db.ProductTable
import vegancheckteam.plante_server.db.ShopTable
import vegancheckteam.plante_server.db.UserQuizTable
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.cmds.AssignModeratorTaskParams
import vegancheckteam.plante_server.cmds.AssignedModeratorTasksDataParams
import vegancheckteam.plante_server.cmds.BanMeParams
import vegancheckteam.plante_server.cmds.CreateUpdateProductParams
import vegancheckteam.plante_server.cmds.LoginParams
import vegancheckteam.plante_server.cmds.MakeReportParams
import vegancheckteam.plante_server.cmds.AllModeratorTasksDataParams
import vegancheckteam.plante_server.cmds.DeleteUserParams
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
import vegancheckteam.plante_server.cmds.SignOutAllParams
import vegancheckteam.plante_server.cmds.UnresolveModeratorTaskParams
import vegancheckteam.plante_server.cmds.UpdateUserDataParams
import vegancheckteam.plante_server.cmds.UserDataParams
import vegancheckteam.plante_server.cmds.UserQuizDataParams
import vegancheckteam.plante_server.cmds.UserQuizParams
import vegancheckteam.plante_server.cmds.assignModeratorTask
import vegancheckteam.plante_server.cmds.assignedModeratorTasksData
import vegancheckteam.plante_server.cmds.banMe
import vegancheckteam.plante_server.cmds.createUpdateProduct
import vegancheckteam.plante_server.cmds.loginUser
import vegancheckteam.plante_server.cmds.makeReport
import vegancheckteam.plante_server.cmds.allModeratorTasksData
import vegancheckteam.plante_server.cmds.deleteUser
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
import vegancheckteam.plante_server.cmds.signOutAll
import vegancheckteam.plante_server.cmds.unresolveModeratorTask
import vegancheckteam.plante_server.cmds.updateUserData
import vegancheckteam.plante_server.cmds.userData
import vegancheckteam.plante_server.cmds.userQuiz
import vegancheckteam.plante_server.cmds.userQuizData

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
