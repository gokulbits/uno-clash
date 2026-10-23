package dev.colorclash.net

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A TCP relay between one client and the host that can misbehave on demand, standing in for a
 * bad Wi-Fi link: [dropAll] cuts connections like a Wi-Fi blip, and [stalled] stops forwarding
 * without closing anything, like a phone frozen in the background or a link that silently died.
 */
class ChaosProxy(private val targetPort: Int) : AutoCloseable {
    private val server = ServerSocket(0)
    private val links = CopyOnWriteArrayList<Socket>()
    @Volatile private var open = true
    @Volatile var stalled = false

    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true, name = "chaos-accept") {
            while (open) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                var upstream: Socket? = null
                try {
                    val up = Socket("127.0.0.1", targetPort).also { upstream = it }
                    links += client
                    links += up
                    pump(client.getInputStream(), up.getOutputStream(), client, up)
                    pump(up.getInputStream(), client.getOutputStream(), client, up)
                } catch (_: Exception) {
                    // Refused, or cut by dropAll mid-setup. Lose this connection, not the whole proxy.
                    runCatching { client.close() }
                    runCatching { upstream?.close() }
                }
            }
        }
    }

    fun dropAll() {
        links.forEach { runCatching { it.close() } }
        links.clear()
    }

    override fun close() {
        open = false
        runCatching { server.close() }
        dropAll()
    }

    private fun pump(from: InputStream, to: OutputStream, a: Socket, b: Socket) = thread(isDaemon = true, name = "chaos-pump") {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = from.read(buffer)
                if (n < 0) break
                // Hold the bytes while stalled; TCP backpressure takes care of the rest.
                while (stalled && open) Thread.sleep(10)
                to.write(buffer, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { a.close() }
            runCatching { b.close() }
        }
    }
}
