package vegancheckteam.plante_server

import cmds.deprecated.LoginParams
import cmds.deprecated.RegisterParams
import cmds.deprecated.loginUser
import cmds.deprecated.registerUser
import cmds.moderation.AllModeratorTasksDataParams
import cmds.moderation.AssignModeratorTaskParams
import cmds.moderation.AssignedModeratorTasksDataParams
import cmds.moderation.DeleteUserParams
import cmds.moderation.ModerateProductVegStatusesParams
import cmds.moderation.ProductChangesDataParams
import cmds.moderation.ProductPresenceVotesDataParams
import cmds.moderation.RejectModeratorTaskParams
import cmds.moderation.ResolveModeratorTaskParams
import cmds.moderation.UnresolveModeratorTaskParams
import cmds.moderation.allModeratorTasksData
import cmds.moderation.assignModeratorTask
import cmds.moderation.assignedModeratorTasksData
import cmds.moderation.deleteUser
import cmds.moderation.moderateProductVegStatuses
import cmds.moderation.productChangesData
import cmds.moderation.productPresenceVotesData
import cmds.moderation.rejectModeratorTask
import cmds.moderation.resolveModeratorTask
import cmds.moderation.unresolveModeratorTask
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
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
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.receive
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import java.io.File
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import vegancheckteam.plante_server.auth.CioHttpTransport
import vegancheckteam.plante_server.auth.JwtController
import vegancheckteam.plante_server.auth.userPrincipal
import vegancheckteam.plante_server.cmds.BanMeParams
import vegancheckteam.plante_server.cmds.CreateShopParams
import vegancheckteam.plante_server.cmds.CreateUpdateProductParams
import vegancheckteam.plante_server.cmds.LoginOrRegisterUserParams
import vegancheckteam.plante_server.cmds.MakeReportParams
import vegancheckteam.plante_server.cmds.MobileAppConfigDataParams
import vegancheckteam.plante_server.cmds.ProductDataParams
import vegancheckteam.plante_server.cmds.ProductPresenceVoteParams
import vegancheckteam.plante_server.cmds.ProductScanParams
import vegancheckteam.plante_server.cmds.ProductsAtShopsDataParams
import vegancheckteam.plante_server.cmds.ProductsDataParams
import vegancheckteam.plante_server.cmds.PutProductToShopParams
import vegancheckteam.plante_server.cmds.ShopsDataParams
import vegancheckteam.plante_server.cmds.ShopsDataRequestBody
import vegancheckteam.plante_server.cmds.ShopsInBoundsDataParams
import vegancheckteam.plante_server.cmds.SignOutAllParams
import vegancheckteam.plante_server.cmds.UpdateUserDataParams
import vegancheckteam.plante_server.cmds.UserDataParams
import vegancheckteam.plante_server.cmds.UserQuizDataParams
import vegancheckteam.plante_server.cmds.UserQuizParams
import vegancheckteam.plante_server.cmds.banMe
import vegancheckteam.plante_server.cmds.createShop
import vegancheckteam.plante_server.cmds.createUpdateProduct
import vegancheckteam.plante_server.cmds.loginOrRegisterUser
import vegancheckteam.plante_server.cmds.makeReport
import vegancheckteam.plante_server.cmds.mobileAppConfigData
import vegancheckteam.plante_server.cmds.moderation.ChangeModeratorTaskLangParams
import vegancheckteam.plante_server.cmds.moderation.ClearProductVegStatusesParams
import vegancheckteam.plante_server.cmds.moderation.CountModeratorTasksParams
import vegancheckteam.plante_server.cmds.moderation.DeleteShopParams
import vegancheckteam.plante_server.cmds.moderation.LatestProductsAddedToShopsDataParams
import vegancheckteam.plante_server.cmds.moderation.ModeratorTaskDataParams
import vegancheckteam.plante_server.cmds.moderation.ModeratorsActivitiesParams
import vegancheckteam.plante_server.cmds.moderation.OFF_PROXY_PATH
import vegancheckteam.plante_server.cmds.moderation.SpecifyModeratorChoiceReasonParams
import vegancheckteam.plante_server.cmds.moderation.UsersDataParams
import vegancheckteam.plante_server.cmds.moderation.changeModeratorTaskLang
import vegancheckteam.plante_server.cmds.moderation.clearProductVegStatuses
import vegancheckteam.plante_server.cmds.moderation.countModeratorTasks
import vegancheckteam.plante_server.cmds.moderation.deleteShop
import vegancheckteam.plante_server.cmds.moderation.latestProductsAddedToShopsData
import vegancheckteam.plante_server.cmds.moderation.moderatorTaskData
import vegancheckteam.plante_server.cmds.moderation.moderatorsActivities
import vegancheckteam.plante_server.cmds.moderation.offProxyGet
import vegancheckteam.plante_server.cmds.moderation.specifyModeratorChoiceReasonParams
import vegancheckteam.plante_server.cmds.moderation.usersData
import vegancheckteam.plante_server.cmds.productData
import vegancheckteam.plante_server.cmds.productPresenceVote
import vegancheckteam.plante_server.cmds.productScan
import vegancheckteam.plante_server.cmds.productsAtShopsData
import vegancheckteam.plante_server.cmds.productsData
import vegancheckteam.plante_server.cmds.putProductToShop
import vegancheckteam.plante_server.cmds.shopsData
import vegancheckteam.plante_server.cmds.shopsInBoundsData
import vegancheckteam.plante_server.cmds.signOutAll
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
import vegancheckteam.plante_server.db.ShopsValidationQueueTable
import vegancheckteam.plante_server.db.UserQuizTable
import vegancheckteam.plante_server.db.UserTable
import vegancheckteam.plante_server.workers.ShopsValidationWorker

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val configIndx = args.indexOf("--config-path")
        if (configIndx >= 0) {
            Config.initFromFile(args[configIndx+1])
        }

        val logsDirPath = System.getenv("LOG_DEST")
        if (logsDirPath != null) {
            val logsDir = File(logsDirPath)
            if (!logsDir.exists()) {
                val created = logsDir.mkdirs()
                if (!created) {
                    throw Error("Couldn't create logs dir: $logsDirPath")
                }
            } else if (!logsDir.isDirectory) {
                throw Error("Provided logs dir is not a dir: $logsDirPath")
            }
        } else {
            throw Error("Must provide a value to evn var LOG_DEST - directory for logs")
        }

        io.ktor.server.cio.EngineMain.main(args)
        ShopsValidationWorker.stop()
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
        format { call ->
            val responseStatus = call.response.status() ?: "Unhandled"
            val uri = call.request.uri
            val user = call.userPrincipal?.user
            val userStr = user?.let { "${it.id} (${it.name})" } ?: "{Invalid}"
            "User: $userStr, req: $uri, resp status: $responseStatus"
        }
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
            level = LogLevel.INFO
        }
        install(HttpTimeout)
    }
    GlobalStorage.httpClient = client
    GlobalStorage.httpTransport = CioHttpTransport(client)
    GlobalStorage.logger = log

    ShopsValidationWorker.start(client, testing)

    routing {
        get<RegisterParams> { call.respond(registerUser(it, client, testing)) }
        get<LoginParams> { call.respond(loginUser(it, client, testing)) }
        get<LoginOrRegisterUserParams> { call.respond(loginOrRegisterUser(it, client, testing)) }

        authenticate {
            getAuthed<BanMeParams> { _, user ->
                if (!testing) {
                    return@getAuthed
                }
                call.respond(banMe(user))
            }
            getAuthed<UserDataParams> { _, user ->
                call.respond(userData(user))
            }
            getAuthed<UsersDataParams> { params, user ->
                call.respond(usersData(params, user))
            }
            getAuthed<MobileAppConfigDataParams> { params, user ->
                call.respond(mobileAppConfigData(params, user, testing))
            }
            getAuthed<UpdateUserDataParams> { params, user ->
                call.respond(updateUserData(params, user))
            }
            getAuthed<SignOutAllParams> { _, user ->
                call.respond(signOutAll(user))
            }
            getAuthed<CreateUpdateProductParams> { params, user ->
                call.respond(createUpdateProduct(params, user))
            }
            getAuthed<ProductDataParams> { params, _ ->
                call.respond(productData(params))
            }
            getAuthed<ProductsDataParams> { params, _ ->
                call.respond(productsData(params))
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
            getAuthed<RejectModeratorTaskParams> { params, user ->
                call.respond(rejectModeratorTask(params, user))
            }
            getAuthed<AllModeratorTasksDataParams> { params, user ->
                call.respond(allModeratorTasksData(params, user, testing))
            }
            getAuthed<ModeratorTaskDataParams> { params, user ->
                call.respond(moderatorTaskData(params, user))
            }
            getAuthed<ModeratorsActivitiesParams> { params, user ->
                call.respond(moderatorsActivities(params, user))
            }
            getAuthed<ResolveModeratorTaskParams> { params, user ->
                call.respond(resolveModeratorTask(params, user, testing))
            }
            getAuthed<UnresolveModeratorTaskParams> { params, user ->
                call.respond(unresolveModeratorTask(params, user))
            }
            getAuthed<ChangeModeratorTaskLangParams> { params, user ->
                call.respond(changeModeratorTaskLang(params, user))
            }
            getAuthed<ModerateProductVegStatusesParams> { params, user ->
                call.respond(moderateProductVegStatuses(params, user))
            }
            getAuthed<ClearProductVegStatusesParams> { params, user ->
                call.respond(clearProductVegStatuses(params, user))
            }
            getAuthed<SpecifyModeratorChoiceReasonParams> { params, user ->
                call.respond(specifyModeratorChoiceReasonParams(params, user))
            }
            getAuthed<CountModeratorTasksParams> { params, user ->
                call.respond(countModeratorTasks(params, user))
            }
            getAuthed<LatestProductsAddedToShopsDataParams> { params, user ->
                call.respond(latestProductsAddedToShopsData(params, user))
            }
            getAuthed<DeleteUserParams> { params, user ->
                call.respond(deleteUser(params, user))
            }
            getAuthed<DeleteShopParams> { params, user ->
                call.respond(deleteShop(params, user))
            }
            getAuthed<PutProductToShopParams> { params, user ->
                call.respond(putProductToShop(params, user, testing))
            }
            getAuthed<ProductsAtShopsDataParams> { params, _ ->
                call.respond(productsAtShopsData(params))
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
            getAuthed<ShopsDataParams> { _, _ ->
                val body = call.receive<ShopsDataRequestBody>()
                call.respond(shopsData(body))
            }
            getAuthed<ShopsInBoundsDataParams> { params, _ ->
                call.respond(shopsInBoundsData(params))
            }

            route("$OFF_PROXY_PATH/{...}", HttpMethod.Get) {
                handle {
                    val user = call.userPrincipal?.user
                    validateUser(user)?.let { error ->
                        call.respond(error)
                        return@handle
                    }
                    call.respond(offProxyGet(call, user!!, client))
                }
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
            ShopsValidationQueueTable,
        )
    }
}
