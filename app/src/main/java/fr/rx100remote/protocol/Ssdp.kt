package fr.rx100remote.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * SSDP discovery of the Sony "ScalarWebAPI" service, written from the public
 * Camera Remote API description (M-SEARCH + device description XML).
 */
object Ssdp {
    const val ST = "urn:schemas-sony-com:service:ScalarWebAPI:1"
    private const val ADDR = "239.255.255.250"
    private const val PORT = 1900

    fun searchMessage(mx: Int = 1): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $ADDR:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $mx\r\n" +
            "ST: $ST\r\n\r\n"

    /** Extracts the LOCATION header of an SSDP response, or null if it is not ours. */
    fun parseLocation(response: String): String? {
        val headers = response.split("\r\n", "\n").mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().uppercase() to line.substring(i + 1).trim()
        }.toMap()
        if (headers["ST"]?.equals(ST, ignoreCase = true) == false) return null
        return headers["LOCATION"]?.takeIf { it.startsWith("http") }
    }

    /**
     * Parses the device description XML and returns serviceType -> endpoint URL,
     * e.g. "camera" -> "http://192.168.122.1:8080/sony/camera".
     * Regex on purpose: the XML is tiny and namespace prefixes vary between models.
     */
    fun parseServices(xml: String): Map<String, String> {
        val serviceBlock = Regex("<(?:\\w+:)?X_ScalarWebAPI_Service>(.*?)</(?:\\w+:)?X_ScalarWebAPI_Service>", RegexOption.DOT_MATCHES_ALL)
        fun tag(block: String, name: String) =
            Regex("<(?:\\w+:)?$name>\\s*(.*?)\\s*</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)
        return serviceBlock.findAll(xml).mapNotNull { m ->
            val type = tag(m.groupValues[1], "X_ScalarWebAPI_ServiceType") ?: return@mapNotNull null
            val url = tag(m.groupValues[1], "X_ScalarWebAPI_ActionList_URL") ?: return@mapNotNull null
            type to url.trimEnd('/') + "/" + type
        }.toMap()
    }

    /**
     * Sends M-SEARCH and returns the first matching LOCATION, or null on timeout.
     * [prepare] lets the caller bind the socket to a specific Android Network.
     */
    fun search(timeoutMs: Int = 3000, attempts: Int = 3, prepare: (DatagramSocket) -> Unit = {}, log: (String) -> Unit = {}): String? {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))
            prepare(socket)
            socket.soTimeout = timeoutMs
            val msg = searchMessage().toByteArray()
            val buf = ByteArray(2048)
            repeat(attempts) { attempt ->
                log("SSDP M-SEARCH #${attempt + 1}")
                socket.send(DatagramPacket(msg, msg.size, InetSocketAddress(ADDR, PORT)))
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, 0, packet.length)
                    log("SSDP <- ${packet.address.hostAddress}\n$text")
                    parseLocation(text)?.let { return it }
                }
            }
        }
        return null
    }
}
