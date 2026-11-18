package fr.rx100remote.protocol

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class CameraApiException(val code: Int, message: String) : Exception("[$code] $message")

/**
 * Minimal JSON-RPC client for the Sony Camera Remote API.
 * [endpoints] maps service type ("camera", "guide", ...) to its URL.
 * Calls are blocking: run them off the main thread.
 */
class CameraApi(
    private val http: OkHttpClient,
    val endpoints: Map<String, String>,
    private val log: (String) -> Unit = {},
) {
    private val ids = AtomicInteger(1)

    /** [quiet] skips the request/response log lines (for polling; the caller logs changes). */
    fun call(method: String, params: JSONArray = JSONArray(), service: String = "camera", version: String = "1.0", quiet: Boolean = false): JSONArray {
        val url = endpoints[service] ?: throw CameraApiException(-1, "service '$service' absent de la description")
        val body = buildRequest(method, params, ids.getAndIncrement(), version)
        if (!quiet) log("-> $url\n$body")
        val request = Request.Builder().url(url).post(body.toRequestBody(JSON)).build()
        val text = http.newCall(request).execute().use { resp ->
            val t = resp.body.string()
            if (!quiet || !resp.isSuccessful) log("<- HTTP ${resp.code}\n$t")
            if (!resp.isSuccessful) throw CameraApiException(resp.code, "HTTP ${resp.code}")
            t
        }
        return parseResponse(text)
    }

    /** Returns the method names announced by getAvailableApiList. */
    fun availableApis(): Set<String> = parseApiList(call("getAvailableApiList"))

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun buildRequest(method: String, params: JSONArray, id: Int, version: String): String =
            JSONObject()
                .put("method", method)
                .put("params", params)
                .put("id", id)
                .put("version", version)
                .toString()

        /** Returns "result" (or "results"), or throws on "error": [code, message]. */
        fun parseResponse(text: String): JSONArray {
            val json = JSONObject(text)
            json.optJSONArray("error")?.let { throw CameraApiException(it.optInt(0, -1), it.optString(1, "erreur inconnue")) }
            return json.optJSONArray("result") ?: json.optJSONArray("results") ?: JSONArray()
        }

        fun parseApiList(result: JSONArray): Set<String> {
            val list = result.optJSONArray(0) ?: return emptySet()
            return (0 until list.length()).map { list.getString(it) }.toSet()
        }

        /** Default endpoints when SSDP fails, from a base like http://192.168.122.1:8080/sony */
        fun defaultEndpoints(base: String): Map<String, String> {
            val b = base.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
            return listOf("camera", "guide", "system", "avContent").associateWith { "$b/$it" }
        }
    }
}
