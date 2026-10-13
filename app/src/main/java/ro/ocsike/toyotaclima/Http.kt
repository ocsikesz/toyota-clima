package ro.ocsike.toyotaclima

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpResponse(val code: Int, val body: String, private val headers: Map<String, List<String>>) {
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
}

object Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        followRedirects: Boolean = true,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 30_000,
    ): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = followRedirects
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.useCaches = false
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = try {
                if (code >= 400) conn.errorStream else conn.inputStream
            } catch (e: Exception) {
                null
            }
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val hdrs = HashMap<String, List<String>>()
            for ((k, v) in conn.headerFields) {
                if (k != null) hdrs[k.lowercase()] = v
            }
            return HttpResponse(code, text, hdrs)
        } finally {
            conn.disconnect()
        }
    }

    fun form(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
}
