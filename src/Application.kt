package vegancheckteam.plante_server

import cmds.avatar.USER_AVATAR_DATA
import cmds.avatar.USER_AVATAR_DATA_USER_ID_PARAM
import cmds.avatar.USER_AVATAR_UPLOAD
import cmds.avatar.userAvatarData
import cmds.avatar.userAvatarUpload
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
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.receive
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
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
import vegancheckteam.plante_server.cmds.UserContributionsDataParams
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
import vegancheckteam.plante_server.cmds.moderation.MoveProductsDeleteShopParams
import vegancheckteam.plante_server.cmds.moderation.RecordCustomModerationActionParams
import vegancheckteam.plante_server.cmds.moderation.SpecifyModeratorChoiceReasonParams
import vegancheckteam.plante_server.cmds.moderation.UsersDataParams
import vegancheckteam.plante_server.cmds.moderation.changeModeratorTaskLang
import vegancheckteam.plante_server.cmds.moderation.clearProductVegStatuses
import vegancheckteam.plante_server.cmds.moderation.countModeratorTasks
import vegancheckteam.plante_server.cmds.moderation.deleteShop
import vegancheckteam.plante_server.cmds.moderation.latestProductsAddedToShopsData
import vegancheckteam.plante_server.cmds.moderation.moderatorTaskData
import vegancheckteam.plante_server.cmds.moderation.moderatorsActivities
import vegancheckteam.plante_server.cmds.moderation.moveProductsDeleteShop
import vegancheckteam.plante_server.cmds.moderation.recordCustomModerationAction
import vegancheckteam.plante_server.cmds.moderation.specifyModeratorChoiceReasonParams
import vegancheckteam.plante_server.cmds.moderation.usersData
import vegancheckteam.plante_server.cmds.off_proxy.OFF_PROXY_GET_PATH
import vegancheckteam.plante_server.cmds.off_proxy.OFF_PROXY_MULTIPART_PATH
import vegancheckteam.plante_server.cmds.off_proxy.OFF_PROXY_POST_FORM_PATH
import vegancheckteam.plante_server.cmds.off_proxy.offProxyGet
import vegancheckteam.plante_server.cmds.off_proxy.offProxyMultipart
import vegancheckteam.plante_server.cmds.off_proxy.offProxyPostForm
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
import vegancheckteam.plante_server.cmds.userContributionsData
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
import vegancheckteam.plante_server.db.UserContributionTable
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
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    mainServerInit()

    install(Locations)

    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            val uri = call.request.uri
            // Prometheus sends a metrics request
            // each 5 seconds
            uri != "/${Config.instance.metricsEndpoint}"
        }
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

    install(CORS) {
        method(HttpMethod.Options)
        header(HttpHeaders.Authorization)
        header(HttpHeaders.ContentType)
        allowCredentials = true
        allowNonSimpleContentTypes = true
        if (Config.instance.allowCors) {
            anyHost()
        } else {
            host("planteapp.vercel.app", listOf("http", "https"))
            host("localhost:3000")
        }
    }

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = prometheusRegistry
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics(),
            UptimeMetrics(),
        )
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
        get<ProductDataParams> { params ->
            call.respond(productData(params))
        }

        route("/${Config.instance.metricsEndpoint}", HttpMethod.Get) {
            handle {
                call.respond(prometheusRegistry.scrape())
            }
        }

        authenticate {
            authedLocation<BanMeParams> { _, user ->
                if (!testing) {
                    return@authedLocation
                }
                call.respond(banMe(user))
            }
            authedLocation<UserDataParams> { _, user ->
                call.respond(userData(user))
            }
            authedLocation<UsersDataParams> { params, user ->
                call.respond(usersData(params, user))
            }
            authedLocation<MobileAppConfigDataParams> { params, user ->
                call.respond(mobileAppConfigData(params, user, testing))
            }
            authedLocation<UpdateUserDataParams> { params, user ->
                call.respond(updateUserData(params, user))
            }
            authedLocation<SignOutAllParams> { _, user ->
                call.respond(signOutAll(user))
            }
            authedLocation<CreateUpdateProductParams> { params, user ->
                call.respond(createUpdateProduct(params, user, testing))
            }
            authedLocation<ProductsDataParams> { params, _ ->
                call.respond(productsData(params))
            }
            authedLocation<ProductChangesDataParams> { params, user ->
                call.respond(productChangesData(params, user))
            }
            authedLocation<UserQuizParams> { params, user ->
                call.respond(userQuiz(params, user))
            }
            authedLocation<UserQuizDataParams> { _, user ->
                call.respond(userQuizData(user))
            }
            authedLocation<MakeReportParams> { params, user ->
                call.respond(makeReport(params, user, testing))
            }
            authedLocation<ProductScanParams> { params, user ->
                call.respond(productScan(params, user, testing))
            }
            authedLocation<AssignModeratorTaskParams> { params, user ->
                call.respond(assignModeratorTask(params, user, testing))
            }
            authedLocation<AssignedModeratorTasksDataParams> { params, user ->
                call.respond(assignedModeratorTasksData(params, user, testing))
            }
            authedLocation<RejectModeratorTaskParams> { params, user ->
                call.respond(rejectModeratorTask(params, user))
            }
            authedLocation<AllModeratorTasksDataParams> { params, user ->
                call.respond(allModeratorTasksData(params, user, testing))
            }
            authedLocation<ModeratorTaskDataParams> { params, user ->
                call.respond(moderatorTaskData(params, user))
            }
            authedLocation<ModeratorsActivitiesParams> { params, user ->
                call.respond(moderatorsActivities(params, user))
            }
            authedLocation<ResolveModeratorTaskParams> { params, user ->
                call.respond(resolveModeratorTask(params, user, testing))
            }
            authedLocation<UnresolveModeratorTaskParams> { params, user ->
                call.respond(unresolveModeratorTask(params, user))
            }
            authedLocation<ChangeModeratorTaskLangParams> { params, user ->
                call.respond(changeModeratorTaskLang(params, user))
            }
            authedLocation<ModerateProductVegStatusesParams> { params, user ->
                call.respond(moderateProductVegStatuses(params, user))
            }
            authedLocation<ClearProductVegStatusesParams> { params, user ->
                call.respond(clearProductVegStatuses(params, user))
            }
            authedLocation<SpecifyModeratorChoiceReasonParams> { params, user ->
                call.respond(specifyModeratorChoiceReasonParams(params, user))
            }
            authedLocation<CountModeratorTasksParams> { params, user ->
                call.respond(countModeratorTasks(params, user))
            }
            authedLocation<LatestProductsAddedToShopsDataParams> { params, user ->
                call.respond(latestProductsAddedToShopsData(params, user))
            }
            authedLocation<DeleteUserParams> { params, user ->
                call.respond(deleteUser(params, user))
            }
            authedLocation<DeleteShopParams> { params, user ->
                call.respond(deleteShop(params, user))
            }
            authedLocation<MoveProductsDeleteShopParams> { params, user ->
                call.respond(moveProductsDeleteShop(params, user, testing, client))
            }
            authedLocation<PutProductToShopParams> { params, user ->
                call.respond(putProductToShop(params, user, testing))
            }
            authedLocation<ProductsAtShopsDataParams> { params, _ ->
                call.respond(productsAtShopsData(params))
            }
            authedLocation<ProductPresenceVoteParams> { params, user ->
                call.respond(productPresenceVote(params, user, testing))
            }
            authedLocation<ProductPresenceVotesDataParams> { params, user ->
                call.respond(productPresenceVotesData(params, user))
            }
            authedLocation<CreateShopParams> { params, user ->
                call.respond(createShop(params, user, testing, client))
            }
            authedLocation<ShopsDataParams> { _, _ ->
                val body = call.receive<ShopsDataRequestBody>()
                call.respond(shopsData(body))
            }
            authedLocation<ShopsInBoundsDataParams> { params, _ ->
                call.respond(shopsInBoundsData(params))
            }
            authedLocation<RecordCustomModerationActionParams> { params, user ->
                call.respond(recordCustomModerationAction(params, user, testing))
            }
            authedLocation<UserContributionsDataParams> { params, user ->
                call.respond(userContributionsData(params, user))
            }

            route(USER_AVATAR_UPLOAD, HttpMethod.Post) {
                authedRouteCustomResponse { call, user ->
                    userAvatarUpload(call, user)
                }
            }
            route("$USER_AVATAR_DATA/{$USER_AVATAR_DATA_USER_ID_PARAM}", HttpMethod.Get) {
                authedRouteCustomResponse { call, user ->
                    userAvatarData(call, user)
                }
            }

            route("$OFF_PROXY_GET_PATH/{...}", HttpMethod.Get) {
                authedRoute { call, user ->
                    offProxyGet(call, user, client, testing)
                }
            }
            route("$OFF_PROXY_POST_FORM_PATH/{...}", HttpMethod.Post) {
                authedRoute { call, _ ->
                    offProxyPostForm(call, client, testing)
                }
            }
            route("$OFF_PROXY_MULTIPART_PATH/{...}") {
                authedRoute { call, _ ->
                    offProxyMultipart(call, client, testing)
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
            UserContributionTable,
        )
    }
}
