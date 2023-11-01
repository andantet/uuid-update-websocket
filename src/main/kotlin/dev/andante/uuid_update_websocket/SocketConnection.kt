package dev.andante.uuid_update_websocket

import io.ktor.websocket.DefaultWebSocketSession
import java.util.UUID

/**
 * Represents a connection with a UUID.
 */
data class SocketConnection(
    /**
     * The web socket session.
     */
    val session: DefaultWebSocketSession,

    /**
     * The UUID of the player.
     */
    val uuid: UUID
) {
    override fun toString(): String {
        return "SocketConnection[$uuid]"
    }
}
