package dev.colorclash.app.update

import android.content.Context
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    /** The APK is staged and the system installer is asking the user. */
    data class Installing(val release: Release) : UpdateState
    data class Failed(val release: Release, val message: String) : UpdateState
}

/** The version this phone is running, or null if the package manager won't say. */
fun installedVersion(context: Context): String? =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

/**
 * Checks GitHub for a newer release once per launch and, if the player wants it, streams the APK
 * straight into a package installer session. The game itself never needs the internet, so a failed
 * check stays silent; only a download the player asked for reports an error.
 */
class Updater(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val checked = AtomicBoolean(false)
    private var work: Job? = null

    fun checkOnce() {
        if (!checked.compareAndSet(false, true)) return
        scope.launch {
            val body = runCatching { fetch(LATEST_RELEASE_URL) }.getOrNull() ?: return@launch
            val release = parseLatestRelease(body) ?: return@launch
            if (isNewer(installedVersion(context), release.version)) _state.value = UpdateState.Available(release)
        }
    }

    fun download(release: Release) {
        work?.cancel()
        _state.value = UpdateState.Downloading(release, 0)
        work = scope.launch {
            runCatching { install(release) }
                .onSuccess { _state.value = UpdateState.Installing(release) }
                .onFailure {
                    if (it is CancellationException) throw it
                    _state.value = UpdateState.Failed(release, "Couldn't download the update. ${it.message ?: ""}".trim())
                }
        }
    }

    /** "Not now", or the player closing the prompt mid-download. */
    fun dismiss() {
        work?.cancel()
        work = null
        _state.value = UpdateState.Idle
    }

    init {
        scope.launch {
            InstallReceiver.failures.collect { message ->
                val release = (_state.value as? UpdateState.Installing)?.release ?: return@collect
                _state.value = UpdateState.Failed(release, message)
            }
        }
    }

    private suspend fun install(release: Release) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                val connection = open(release.apkUrl)
                try {
                    val total = if (release.apkBytes > 0) release.apkBytes else connection.contentLengthLong
                    // Checked whenever the release publishes one. The platform's signature check is
                    // the real gate; this catches a truncated or swapped download before it's staged.
                    val digest = release.sha256?.let { MessageDigest.getInstance("SHA-256") }
                    session.openWrite(SESSION_NAME, 0, total).use { out ->
                        connection.inputStream.use { copy(it, out, total, digest) { percent ->
                            _state.value = UpdateState.Downloading(release, percent)
                        } }
                        session.fsync(out)
                    }
                    val actual = digest?.digest()?.joinToString("") { "%02x".format(it) }
                    if (actual != null && !actual.equals(release.sha256, ignoreCase = true)) {
                        error("the download didn't match the checksum GitHub published")
                    }
                } finally {
                    connection.disconnect()
                }
                session.commit(InstallReceiver.statusSender(context, id))
            }
        } catch (e: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private fun fetch(url: String): String {
        val connection = open(url, accept = "application/vnd.github+json")
        try {
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The APK url arrives inside a JSON body, so it is untrusted input: only GitHub over TLS gets
     * fetched. Cleartext is permitted app-wide for LAN play, so an http:// url would otherwise be
     * downloaded in the open and staged for install.
     */
    private fun allowed(url: URL): Boolean {
        if (!url.protocol.equals("https", ignoreCase = true)) return false
        val host = url.host.lowercase()
        return host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com")
    }

    private fun trusted(raw: String): URL? = runCatching { URL(raw) }.getOrNull()?.takeIf(::allowed)

    private fun open(raw: String, accept: String? = null): HttpURLConnection {
        var target = trusted(raw) ?: error("that release link isn't GitHub over HTTPS")
        var redirects = 0
        while (true) {
            val connection = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                // Followed by hand, so every hop is checked against the allowlist and not just the first.
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "color-clash")
                accept?.let { setRequestProperty("Accept", it) }
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (code !in REDIRECTS || location == null || redirects++ >= MAX_REDIRECTS) error("GitHub answered $code")
            target = trusted(URL(target, location).toString()) ?: error("that release link redirects away from GitHub")
        }
    }

    private suspend inline fun copy(
        input: InputStream,
        out: OutputStream,
        total: Long,
        digest: MessageDigest?,
        onProgress: (Int) -> Unit,
    ) {
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        var reported = -1
        while (true) {
            // Reading a stream doesn't notice cancellation on its own, so check between chunks.
            if (!coroutineContext.isActive) throw CancellationException("Update cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            digest?.update(buffer, 0, read)
            done += read
            val percent = percentOf(done, total)
            if (percent != reported) {
                reported = percent
                onProgress(percent)
            }
        }
    }

    private companion object {
        const val SESSION_NAME = "color-clash.apk"
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
