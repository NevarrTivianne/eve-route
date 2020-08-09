package net.tkhamez.everoute

import io.ktor.application.*
import io.ktor.response.*
//import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
//import io.ktor.content.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.auth.*
//import kotlinx.coroutines.*
import io.ktor.features.StatusPages
import io.ktor.util.KtorExperimentalAPI
import java.io.File

//fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
@KtorExperimentalAPI
fun main() {
    embeddedServer(
        Netty,
        watchPaths = listOf("classes"),
        port = 8080,
        module = Application::module
    ).start(true)
}

//@Suppress("unused") // Referenced in application.conf
//@kotlin.jvm.JvmOverloads
//fun Application.module(testing: Boolean = false) {
@KtorExperimentalAPI
fun Application.module() {

    val config = Config(
        environment.config.property("app.db").getString(),
        environment.config.property("app.clientId").getString(),
        environment.config.property("app.clientSecret").getString(),
        environment.config.property("app.callback").getString(),
        environment.config.property("app.authorizeUrl").getString(),
        environment.config.property("app.accessTokenUrl").getString()
    )

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respondText("Error.", status = HttpStatusCode.InternalServerError)
            throw cause
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized) {
            call.respond(TextContent(
                    "${it.value} ${it.description}",
                    ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                    it
            ))
        }
    }

    install(Sessions) {
        val storage = if (config.callback.contains("localhost:8080")) {
            directorySessionStorage(File(System.getProperty("java.io.tmpdir") + "/.eve-route-sessions"), cached = true)
        } else {
            SessionStorageMemory()
        }
        cookie<Session>("EVE_ROUTE_SESSION", storage) {
            cookie.extensions["SameSite"] = "lax"
            cookie.path = "/"
            serializer = GsonSessionSerializer(Session::class.java)
        }
    }

    install(Authentication) {
        oauth("eve-oauth") {
            client = httpClient
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                        name = "eve",
                        authorizeUrl = config.authorizeUrl,
                        accessTokenUrl = config.accessTokenUrl,
                        requestMethod = HttpMethod.Post,
                        clientId = config.clientId,
                        clientSecret = config.clientSecret,
                        defaultScopes = listOf(
                                "esi-location.read_location.v1",
                                "esi-search.search_structures.v1",
                                "esi-universe.read_structures.v1",
                                "esi-ui.write_waypoint.v1"
                        )
                )
            }
            urlProvider = {
                config.callback
            }
        }
    }

    install(Routing) {
        frontend()
        authentication()
        findGates(config)
        mongo(config.db)
    }
}