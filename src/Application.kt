package vegancheckteam.untitled_vegan_app_server

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.routing.*
import io.ktor.http.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.logging.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import vegancheckteam.untitled_vegan_app_server.db.User
import java.util.*

object Main {
    lateinit var config: Config
    var configInited = false

    @JvmStatic
    fun main(args: Array<String>) {
        val configIndx = args.indexOf("--config-path")
        if (configIndx >= 0) {
            config = Config.fromFile(args[configIndx+1])
            configInited = true
        }
        print("Provided config:\n${config}\n\n")
        io.ktor.server.cio.EngineMain.main(args)
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    if (!Main.configInited) {
        val path = System.getenv("CONFIG_FILE_PATH")
        if (path == null) {
            throw IllegalStateException("Please provide either --config-path or CONFIG_FILE_PATH env variable")
        }
        Main.config = Config.fromFile(path)
        Main.configInited = true
    }

    Database.connect(
        "jdbc:${Main.config.psqlUrl}",
        user = Main.config.psqlUser,
        password = Main.config.psqlPassword)
    transaction {
        SchemaUtils.createMissingTablesAndColumns(User)
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    val client = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.HEADERS
        }
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        get("/register_user/{name}") {
            val obtainedName = call.parameters["name"]!!
            transaction {
                User.insert {
                    it[id] = UUID.randomUUID()
                    it[name] = obtainedName
                }
            }
            call.respond("ok")
        }

        get("/is_registered/{name}") {
            val obtainedName = call.parameters["name"]!!
            val selected = transaction {
                User.select {
                    User.name eq obtainedName
                }.toList()
            }
            call.respond(if (selected.isNotEmpty()) "yep" else "nope")
        }

        get("/delete_user/{name}") {
            val obtainedName = call.parameters["name"]!!
            transaction {
                User.deleteWhere {
                    User.name eq obtainedName
                }
            }
            call.respond("done!")
        }
    }
}

