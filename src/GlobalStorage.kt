package vegancheckteam.plante_server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.api.client.http.HttpTransport
import io.ktor.client.HttpClient

object GlobalStorage {
    lateinit var httpClient: HttpClient
    lateinit var httpTransport: HttpTransport
    val jsonMapper = ObjectMapper().registerModule(KotlinModule())
}
