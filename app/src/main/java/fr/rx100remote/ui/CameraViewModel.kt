package fr.rx100remote.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.rx100remote.net.CameraNetwork
import fr.rx100remote.protocol.CameraApi
import fr.rx100remote.protocol.CameraApiException
import fr.rx100remote.protocol.LiveviewParser
import fr.rx100remote.protocol.Ssdp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class Phase { Idle, Wifi, Discovering, Connecting, Live, Reconnecting, Error }

data class UiState(
    val phase: Phase = Phase.Idle,
    val wifi: String = "Wi-Fi : —",
    val endpoint: String? = null,
    val apis: Set<String> = emptySet(),
    val message: String = "",
    val manualUrl: String = DEFAULT_BASE,
    val mirror: Boolean = false,
    val showStats: Boolean = false,
    val fps: Float = 0f,
    val decodeMs: Long = 0,
    val frameAgeMs: Long = 0,
    val resyncs: Int = 0,
    val busy: Boolean = false,
    val toast: String? = null,
)

const val DEFAULT_BASE = "http://192.168.122.1:8080/sony"

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Latest decoded frame only; older ones are simply replaced. */
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(line: String) {
        val entry = "${timeFmt.format(Date())} $line"
        _logs.update { (it + entry).takeLast(MAX_LOGS) }
        android.util.Log.d("RX100", line)
    }

    private val network = CameraNetwork(app, ::log)
    private var api: CameraApi? = null
    private var session: Job? = null

    private fun http(readTimeoutSec: Long): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
        network.network.value?.let { b.socketFactory(it.socketFactory) }
        return b.build()
    }

    init {
        viewModelScope.launch { network.description.collect { d -> _state.update { it.copy(wifi = d) } } }
    }

    fun setManualUrl(url: String) = _state.update { it.copy(manualUrl = url) }
    fun toggleMirror() = _state.update { it.copy(mirror = !it.mirror) }
    fun toggleStats() = _state.update { it.copy(showStats = !it.showStats) }
    fun toastShown() = _state.update { it.copy(toast = null) }
    fun clearLogs() { _logs.value = emptyList() }

    fun connect(useManual: Boolean = false) {
        session?.cancel()
        session = viewModelScope.launch(Dispatchers.IO) {
            try {
                runSession(useManual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("ERREUR : ${e.javaClass.simpleName}: ${e.message}")
                _state.update { it.copy(phase = Phase.Error, message = explain(e)) }
            }
        }
    }

    fun disconnect() {
        session?.cancel()
        _frame.value = null
        _state.update { it.copy(phase = Phase.Idle, message = "Déconnecté") }
        val a = api
        if (a != null && "stopLiveview" in _state.value.apis) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { a.call("stopLiveview") } }
        }
    }

    private suspend fun runSession(useManual: Boolean) {
        _state.update { it.copy(phase = Phase.Wifi, message = "Attente du Wi-Fi de l'appareil…") }
        network.start()
        if (network.await(10_000) == null) {
            throw IllegalStateException("Pas de réseau Wi-Fi. Connecte le téléphone au Wi-Fi DIRECT-xxxx:DSC-RX100M3 puis réessaie.")
        }

        val endpoints = if (useManual) {
            log("URL manuelle : ${_state.value.manualUrl}")
            CameraApi.defaultEndpoints(_state.value.manualUrl)
        } else discover()
        val camera = CameraApi(http(10), endpoints, ::log).also { api = it }
        _state.update { it.copy(phase = Phase.Connecting, endpoint = endpoints["camera"], message = "Connexion à l'appareil…") }

        var apis = camera.availableApis()
        publishApis(apis)
        if ("startRecMode" in apis && "startLiveview" !in apis) {
            camera.call("startRecMode")
            // The camera needs a moment to switch to shooting mode; poll until liveview shows up.
            for (i in 1..10) {
                delay(500)
                apis = camera.availableApis()
                if ("startLiveview" in apis) break
            }
            publishApis(apis)
        }
        if ("setShootMode" in apis) {
            runCatching { camera.call("setShootMode", JSONArray(listOf("still"))) }
                .onFailure { log("setShootMode ignoré : ${it.message}") }
            apis = camera.availableApis(); publishApis(apis)
        }
        if ("startLiveview" !in apis) throw CameraApiException(-1, "startLiveview non annoncé par l'appareil")

        streamLoop(camera)
    }

    private fun discover(): Map<String, String> {
        _state.update { it.copy(phase = Phase.Discovering, message = "Recherche SSDP de l'appareil…") }
        val location = runCatching { Ssdp.search(prepare = network::bind, log = ::log) }
            .onFailure { log("SSDP erreur : ${it.message}") }.getOrNull()
        if (location != null) {
            val xml = runCatching {
                http(5).newCall(Request.Builder().url(location).build()).execute().use { it.body.string() }
            }.onFailure { log("Description XML erreur : ${it.message}") }.getOrNull()
            if (xml != null) {
                log("Description XML :\n$xml")
                val services = Ssdp.parseServices(xml)
                if ("camera" in services) {
                    log("Services : $services")
                    return services
                }
            }
        }
        val base = _state.value.manualUrl
        log("SSDP sans résultat, repli sur $base")
        return CameraApi.defaultEndpoints(base)
    }

    private fun publishApis(apis: Set<String>) = _state.update { it.copy(apis = apis) }

    /** Runs the liveview; on stream failure, restarts it with backoff before giving up. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun streamLoop(camera: CameraApi) {
        var failures = 0
        while (true) {
            val url = camera.call("startLiveview").getString(0)
            log("Flux live view : $url")
            _state.update { it.copy(phase = Phase.Live, message = "") }
            val started = SystemClock.elapsedRealtime()
            runCatching { readStream(url) }
                .onFailure { if (it is CancellationException) throw it; log("Flux coupé : ${it.javaClass.simpleName}: ${it.message}") }
            // A stream that ran for a while before dropping resets the retry budget.
            failures = if (SystemClock.elapsedRealtime() - started > 10_000) 1 else failures + 1
            if (failures > MAX_RETRIES) throw IllegalStateException("Le flux live view a coupé $MAX_RETRIES fois de suite.")
            _state.update { it.copy(phase = Phase.Reconnecting, message = "Flux coupé, reconnexion (essai $failures/$MAX_RETRIES)…") }
            delay(1000L * failures)
            runCatching { camera.call("stopLiveview") }
        }
    }

    private suspend fun readStream(url: String) = withContext(Dispatchers.IO) {
        // Reader -> conflated channel -> decoder: the decoder always takes the newest JPEG,
        // frames that arrive while it is busy are dropped.
        val latest = Channel<Pair<ByteArray, Long>>(Channel.CONFLATED)
        val decoder = launch(Dispatchers.Default) {
            var count = 0
            var windowStart = SystemClock.elapsedRealtime()
            for ((jpeg, receivedAt) in latest) {
                val t0 = SystemClock.elapsedRealtime()
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
                val now = SystemClock.elapsedRealtime()
                _frame.value = bmp
                count++
                if (now - windowStart >= 1000) {
                    val fps = count * 1000f / (now - windowStart)
                    _state.update { it.copy(fps = fps, decodeMs = now - t0, frameAgeMs = now - receivedAt) }
                    count = 0; windowStart = now
                }
            }
        }
        try {
            http(10).newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw CameraApiException(resp.code, "flux HTTP ${resp.code}")
                val parser = LiveviewParser(resp.body.byteStream())
                var lastResyncs = 0
                while (isActive) {
                    val f = parser.nextFrame()
                    latest.trySend(f.jpeg to SystemClock.elapsedRealtime())
                    if (parser.resyncs != lastResyncs) {
                        lastResyncs = parser.resyncs
                        _state.update { it.copy(resyncs = lastResyncs) }
                    }
                }
            }
        } finally {
            latest.close()
            decoder.cancel()
        }
    }

    fun takePicture() = action("actTakePicture") { camera ->
        val result = try {
            camera.call("actTakePicture")
        } catch (e: CameraApiException) {
            if (e.code != 40403) throw e
            log("Prise longue, awaitTakePicture…")
            camera.call("awaitTakePicture")
        }
        val url = result.optJSONArray(0)?.optString(0)
        "Photo prise" + (url?.let { "\n$it" } ?: "")
    }

    fun zoom(direction: String) = action("actZoom") { camera ->
        camera.call("actZoom", JSONArray(listOf(direction, "1shot")))
        null
    }

    private fun action(method: String, block: (CameraApi) -> String?) {
        val camera = api ?: return
        if (method !in _state.value.apis) {
            _state.update { it.copy(toast = "$method non disponible actuellement") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true) }
            val msg = try {
                block(camera)
            } catch (e: Exception) {
                log("$method erreur : ${e.message}")
                "Échec $method : ${explain(e)}"
            }
            // The available API list changes after actions (e.g. while saving a picture).
            runCatching { publishApis(camera.availableApis()) }
            _state.update { it.copy(busy = false, toast = msg) }
        }
    }

    private fun explain(e: Exception): String = when (e) {
        is CameraApiException -> when (e.code) {
            1 -> "Erreur de l'appareil (Any)"
            3 -> "Paramètre refusé par l'appareil"
            12 -> "Méthode non supportée par ce modèle"
            14 -> "Version d'API non supportée"
            403 -> "Accès refusé (403)"
            40401 -> "Appareil pas prêt (40401)"
            40403 -> "Prise de vue longue en cours"
            else -> e.message ?: "Erreur API"
        }
        is java.net.SocketTimeoutException -> "Délai dépassé : l'appareil ne répond pas. Est-il en mode « Contrôle par smartphone » ?"
        is java.net.ConnectException -> "Connexion refusée : vérifie l'URL et que l'appli Smart Remote est lancée sur l'appareil."
        is java.net.UnknownServiceException -> "HTTP en clair refusé pour cette adresse (network_security_config)."
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        disconnect()
        network.stop()
    }

    companion object {
        private const val MAX_LOGS = 1000
        private const val MAX_RETRIES = 5
    }
}
