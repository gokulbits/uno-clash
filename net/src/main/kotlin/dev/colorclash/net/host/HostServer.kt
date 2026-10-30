package dev.colorclash.net.host

import dev.colorclash.protocol.ClientMessage
import dev.colorclash.protocol.Codec
import dev.colorclash.protocol.DEFAULT_PORT
import dev.colorclash.protocol.JoinError
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.protocol.WS_PATH
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Embedded Ktor WebSocket server that feeds connections into a [GameHost]. */
class HostServer(private val host: GameHost, private val port: Int = DEFAULT_PORT) {
    @Volatile private var server: EmbeddedServer<*, *>? = null
    private val open = AtomicInteger(0)

    /** Listens on every interface and returns the bound port (handy when [port] is 0). */
    suspend fun start(): Int {
        val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                maxFrameSize = MAX_FRAME_BYTES
            }
            routing {
                webSocket(WS_PATH) { serve(this) }
            }
        }
        engine.startSuspend(wait = false)
        server = engine
        return engine.engine.resolvedConnectors().first().port
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        server = null
    }

    /**
     * A phone hosts one room of at most six players, and every connection costs an outbox and two
     * coroutines, so anything past a small multiple of that is a peer misbehaving.
     */
    private suspend fun serve(session: DefaultWebSocketServerSession) {
        if (open.incrementAndGet() > MAX_CONNECTIONS) {
            open.decrementAndGet()
            session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many connections"))
            return
        }
        try {
            read(session)
        } finally {
            open.decrementAndGet()
        }
    }

    private suspend fun read(session: DefaultWebSocketServerSession) {
        val connection = SocketConnection(session)
        val writer = session.launch { connection.pump() }
        var greeted = false
        val helloBy = TimeSource.Monotonic.markNow() + HANDSHAKE_TIMEOUT
        try {
            while (true) {
                // Clients ping every couple of seconds, so silence this long means the link is dead.
                // Before the hello a much shorter leash, counted from the connection opening: a socket
                // that only ever pings would otherwise hold one of the few connection slots for good.
                val wait = if (greeted) host.config.heartbeatTimeout else -helloBy.elapsedNow()
                val frame = withTimeoutOrNull(wait) {
                    session.incoming.receiveCatching().getOrNull()
                } ?: break
                if (frame !is Frame.Text) continue
                val message = try {
                    Codec.decodeClient(frame.readText())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!greeted) {
                        // Most likely an APK from a different version speaking a different dialect.
                        connection.send(
                            ServerMessage.JoinRejected(
                                JoinError.VERSION_MISMATCH,
                                "The host couldn't understand this phone. Install the same build on every phone.",
                            ),
                        )
                        connection.close("Unreadable hello")
                    }
                    continue
                }
                if (message is ClientMessage.Hello) greeted = true
                host.onMessage(connection, message)
            }
        } finally {
            host.onDisconnect(connection)
            connection.close("Disconnected")
            writer.cancel()
        }
    }

    /** Queues outgoing messages so the host actor never waits on a slow socket. */
    private class SocketConnection(private val session: DefaultWebSocketServerSession) : Connection {
        private val outbox = Channel<ServerMessage>(OUTBOX_SIZE)
        @Volatile private var closeReason = ""

        override fun send(message: ServerMessage) {
            // A client this far behind is effectively gone; drop it and let it resync on reconnect.
            if (outbox.trySend(message).isFailure && !outbox.isClosedForSend) close("Too far behind")
        }

        override fun close(reason: String) {
            if (outbox.isClosedForSend) return
            closeReason = reason.take(100)
            outbox.close()
        }

        suspend fun pump() {
            for (message in outbox) session.send(Frame.Text(Codec.encode(message)))
            session.close(CloseReason(CloseReason.Codes.NORMAL, closeReason))
        }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 256L * 1024
        const val OUTBOX_SIZE = 512
        const val MAX_CONNECTIONS = 24
        val HANDSHAKE_TIMEOUT = 5.seconds
    }
}
