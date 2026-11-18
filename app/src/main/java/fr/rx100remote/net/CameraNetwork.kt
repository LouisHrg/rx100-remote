package fr.rx100remote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket

/**
 * Keeps hold of the camera's Wi-Fi network (which has no internet) and binds the
 * whole process to it, so Android/ColorOS do not route our traffic to mobile data.
 * Requesting a Wi-Fi network WITHOUT the INTERNET capability also tells the system
 * an app needs it, which keeps it connected even when validation fails.
 */
class CameraNetwork(context: Context, private val log: (String) -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val _network = MutableStateFlow<Network?>(null)
    val network: StateFlow<Network?> = _network

    private val _description = MutableStateFlow("Wi-Fi : recherche…")
    val description: StateFlow<String> = _description

    private var multicastLock: WifiManager.MulticastLock? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            log("Wi-Fi disponible : $network, liaison du process")
            cm.bindProcessToNetwork(network)
            _network.value = network
        }

        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            val ip = lp.linkAddresses.joinToString { it.address.hostAddress ?: "?" }
            val gw = lp.routes.mapNotNull { it.gateway?.hostAddress }.filter { it != "0.0.0.0" && it != "::" }.distinct().joinToString()
            _description.value = "Wi-Fi lié — IP $ip, passerelle ${gw.ifEmpty { "?" }}"
            log("LinkProperties : IP=$ip passerelle=$gw")
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            log("Capacités : internet=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
                "validé=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
        }

        override fun onLost(network: Network) {
            log("Wi-Fi perdu : $network")
            if (_network.value == network) {
                cm.bindProcessToNetwork(null)
                _network.value = null
                _description.value = "Wi-Fi perdu"
            }
        }

        override fun onUnavailable() {
            log("Aucun Wi-Fi disponible")
            _description.value = "Aucun Wi-Fi : connecte le téléphone au réseau DIRECT-xxxx de l'appareil"
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.requestNetwork(request, callback)
        multicastLock = wifi.createMulticastLock("rx100-ssdp").apply { setReferenceCounted(false); acquire() }
        log("NetworkRequest Wi-Fi (sans internet) envoyée, MulticastLock acquis")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { cm.unregisterNetworkCallback(callback) }
        cm.bindProcessToNetwork(null)
        multicastLock?.release()
        _network.value = null
    }

    suspend fun await(timeoutMs: Long): Network? =
        withTimeoutOrNull(timeoutMs) { network.filterNotNull().first() }

    fun bind(socket: DatagramSocket) {
        _network.value?.bindSocket(socket)
    }
}
