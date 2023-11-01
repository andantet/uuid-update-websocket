package dev.andante.uuid_update_websocket

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Collections
import java.util.UUID

private val logger = LoggerFactory.getLogger("Websocket")

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
            }
        )
    }

    routing {
        val activeConnections = Collections.synchronizedSet<SocketConnection>(LinkedHashSet())
        webSocket("/{uuid}") {
            val uuidString = call.parameters["uuid"]

            // validate uuid exists
            if (uuidString == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "UUID not present"))
                return@webSocket
            }

            // parse uuid parameter
            val uuid = try {
                UUID.fromString(uuidString)
            } catch (_: IllegalArgumentException) {
                logger.info("User failed to connect with invalid UUID: $uuidString")
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid UUID"))
                return@webSocket
            }

            // check connection uuid against active connections
            if (activeConnections.any { it.uuid == uuid }) {
                logger.info("Duplicate UUID attempted to connect: $uuid")
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Already connected"))
                return@webSocket
            }

            // create socket connection
            val socketConnection = SocketConnection(this, uuid)

            // send new connection to all active connections
            activeConnections.forEach { (connection, _) ->
                connection.send("ADD $uuid")
            }

            // send all active connections
            if (activeConnections.isNotEmpty()) {
                val connectionsString = activeConnections.map(SocketConnection::uuid).joinToString()
                send("ADD $connectionsString")
            }

            // register connection
            activeConnections.add(socketConnection)

            // send initial messages
            logger.info("User #${activeConnections.size} connected: $uuid")

            // process incoming messages
            try {
                @Suppress("ControlFlowWithEmptyBody")
                for (frame in incoming) {
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            } finally {
                activeConnections.remove(socketConnection)
                activeConnections.forEach { (connection, _) ->
                    connection.send("DEL $uuid")
                }

                logger.info("User disconnected: $uuid (${activeConnections.size} remain)")
            }
        }
    }
}
