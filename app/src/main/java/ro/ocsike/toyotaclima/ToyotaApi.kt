package ro.ocsike.toyotaclima

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ToyotaException(message: String) : Exception(message)

/**
 * Client minimal pentru Toyota Connected Services Europe.
 * Portat din pytoyoda (controller.py, api.py, const.py) — API neoficial.
 */
class ToyotaApi(private val prefs: Prefs) {

    companion object {
        private const val API_BASE = "https://ctpa-oneapi.tceu-ctp-prd.toyotaconnectedeurope.io"
        private const val AUTHENTICATE_URL =
            "https://b2c-login.toyota-europe.com/json/realms/root/realms/tme/authenticate?authIndexType=service&authIndexValue=oneapp"
        private const val AUTHORIZE_URL =
            "https://b2c-login.toyota-europe.com/oauth2/realms/root/realms/tme/authorize?client_id=oneapp&scope=openid+profile+write&response_type=code&redirect_uri=com.toyota.oneapp:/oauth2Callback&code_challenge=plain&code_challenge_method=plain"
        private const val TOKEN_URL =
            "https://b2c-login.toyota-europe.com/oauth2/realms/root/realms/tme/access_token"
        private const val BASIC_AUTH = "basic b25lYXBwOm9uZWFwcA=="
        private const val CLIENT_ID = "oneapp"
        private const val REDIRECT_URI = "com.toyota.oneapp:/oauth2Callback"
        private const val API_KEY = "tTZipv6liF74PwMfk9Ed68AQ0bISswwf3iHQdqcF"
        private const val CLIENT_VERSION = "2.14.0"

        private const val VEHICLES_ENDPOINT = "/v2/vehicle/guid"
        private const val CLIMATE_CONTROL_ENDPOINT = "/v2/remote/climate-control"

        const val RETURN_OK = "000000"
    }

    // ---------- Autentificare ----------

    fun ensureToken() {
        val now = System.currentTimeMillis()
        if (prefs.accessToken.isNotEmpty() && prefs.tokenExpiresAt - 60_000 > now) return
        if (prefs.refreshToken.isNotEmpty()) {
            try {
                refreshTokens()
                return
            } catch (e: Exception) {
                // refresh expirat sau invalid -> login complet
            }
        }
        login()
    }

    private fun login() {
        if (prefs.username.isBlank() || prefs.password.isBlank()) {
            throw ToyotaException("Completează emailul și parola MyToyota.")
        }
        val tokenId = authenticate()
        val code = authorize(tokenId)
        val resp = Http.request(
            "POST", TOKEN_URL,
            mapOf(
                "authorization" to BASIC_AUTH,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            Http.form(
                mapOf(
                    "client_id" to CLIENT_ID,
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "grant_type" to "authorization_code",
                    "code_verifier" to "plain",
                )
            ),
        )
        if (resp.code != 200) throw ToyotaException("Obținere token eșuată (${resp.code}): ${resp.body.take(200)}")
        saveTokens(JSONObject(resp.body))
    }

    private fun authenticate(): String {
        var data = JSONObject()
        repeat(10) {
            val callbacks = data.optJSONArray("callbacks")
            if (callbacks != null) {
                for (i in 0 until callbacks.length()) {
                    val cb = callbacks.getJSONObject(i)
                    val type = cb.optString("type")
                    val outValue = cb.optJSONArray("output")?.optJSONObject(0)?.optString("value")
                    when {
                        type == "NameCallback" && outValue == "User Name" ->
                            cb.getJSONArray("input").getJSONObject(0).put("value", prefs.username)
                        type == "PasswordCallback" ->
                            cb.getJSONArray("input").getJSONObject(0).put("value", prefs.password)
                        type == "TextOutputCallback" && outValue == "User Not Found" ->
                            throw ToyotaException("Contul MyToyota nu a fost găsit. Verifică emailul.")
                    }
                }
            }
            val resp = Http.request(
                "POST", AUTHENTICATE_URL,
                mapOf("Content-Type" to "application/json"),
                data.toString(),
            )
            if (resp.code != 200) {
                throw ToyotaException("Autentificare eșuată (${resp.code}). Verifică emailul și parola. ${resp.body.take(200)}")
            }
            data = JSONObject(resp.body)
            if (data.has("tokenId")) return data.getString("tokenId")
        }
        throw ToyotaException("Autentificare eșuată: nu am primit tokenId. Contul are autentificare în doi pași?")
    }

    private fun authorize(tokenId: String): String {
        val resp = Http.request(
            "GET", AUTHORIZE_URL,
            mapOf("cookie" to "iPlanetDirectoryPro=$tokenId"),
            followRedirects = false,
        )
        if (resp.code != 302) throw ToyotaException("Autorizare eșuată (${resp.code}): ${resp.body.take(200)}")
        val location = resp.header("location") ?: throw ToyotaException("Autorizare: lipsește redirect-ul.")
        val query = location.substringAfter('?', "")
        for (part in query.split('&')) {
            val k = part.substringBefore('=')
            if (k == "code") return URLDecoder.decode(part.substringAfter('='), "UTF-8")
        }
        throw ToyotaException("Autorizare: nu găsesc codul în redirect.")
    }

    private fun refreshTokens() {
        val resp = Http.request(
            "POST", TOKEN_URL,
            mapOf(
                "authorization" to BASIC_AUTH,
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            Http.form(
                mapOf(
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to REDIRECT_URI,
                    "grant_type" to "refresh_token",
                    "code_verifier" to "plain",
                    "refresh_token" to prefs.refreshToken,
                )
            ),
        )
        if (resp.code != 200) throw ToyotaException("Refresh token eșuat (${resp.code})")
        saveTokens(JSONObject(resp.body))
    }

    private fun saveTokens(json: JSONObject) {
        for (field in listOf("access_token", "id_token", "refresh_token", "expires_in")) {
            if (!json.has(field)) throw ToyotaException("Răspuns token incomplet: lipsește $field")
        }
        val idToken = json.getString("id_token")
        val parts = idToken.split('.')
        if (parts.size < 2) throw ToyotaException("id_token invalid")
        val payload = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
        prefs.accessToken = json.getString("access_token")
        prefs.refreshToken = json.getString("refresh_token")
        prefs.uuid = payload.getString("uuid")
        prefs.tokenExpiresAt = System.currentTimeMillis() + json.getLong("expires_in") * 1000
    }

    fun clearTokens() {
        prefs.accessToken = ""
        prefs.refreshToken = ""
        prefs.uuid = ""
        prefs.tokenExpiresAt = 0
    }

    // ---------- Cereri API ----------

    private fun hmacSha256Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun headers(vin: String?): Map<String, String> {
        val uuid = prefs.uuid
        val h = linkedMapOf(
            "x-api-key" to API_KEY,
            "API_KEY" to API_KEY,
            "x-guid" to uuid,
            "guid" to uuid,
            "x-client-ref" to hmacSha256Hex(CLIENT_VERSION, uuid),
            "x-correlationid" to UUID.randomUUID().toString(),
            "x-appversion" to CLIENT_VERSION,
            "x-channel" to "ONEAPP",
            "x-brand" to "T",
            "x-region" to "EU",
            "x-user-region" to "EU",
            "authorization" to "Bearer ${prefs.accessToken}",
            "user-agent" to "okhttp/4.10.0",
            "Content-Type" to "application/json",
        )
        if (vin != null) h["vin"] = vin
        return h
    }

    private fun api(method: String, endpoint: String, vin: String? = null, body: JSONObject? = null): JSONObject {
        ensureToken()
        val backoffs = longArrayOf(2_000, 4_000)
        var last: HttpResponse? = null
        for (attempt in 0..backoffs.size) {
            val resp = Http.request(method, API_BASE + endpoint, headers(vin), body?.toString())
            if (resp.code == 200 || resp.code == 202) {
                return if (resp.body.isBlank()) JSONObject() else JSONObject(resp.body)
            }
            if (resp.code == 401) {
                // token respins: forțează login complet data viitoare
                clearTokens()
            }
            last = resp
            val transient = resp.code == 429 || resp.code >= 500
            if (!transient || attempt >= backoffs.size) break
            Thread.sleep(backoffs[attempt])
        }
        throw ToyotaException("Toyota API ${last?.code}: ${last?.body?.take(200)}")
    }

    /** Returnează VIN-ul primei mașini din cont (sau cel salvat). */
    fun resolveVin(): String {
        if (prefs.vin.isNotBlank()) return prefs.vin.trim()
        val json = api("GET", VEHICLES_ENDPOINT)
        val list = json.optJSONArray("payload")
        if (list == null || list.length() == 0) throw ToyotaException("Nicio mașină în contul MyToyota.")
        val vin = list.getJSONObject(0).optString("vin")
        if (vin.isBlank()) throw ToyotaException("Nu pot citi VIN-ul mașinii.")
        prefs.vin = vin
        return vin
    }

    /** Trimite comanda de climatizare. Returnează codul de răspuns ("000000" = acceptat). */
    fun climateControl(vin: String, body: JSONObject): String? {
        val json = api("POST", CLIMATE_CONTROL_ENDPOINT, vin, body)
        val payload = json.optJSONObject("payload") ?: return null
        return if (payload.has("returnCode")) payload.optString("returnCode") else null
    }
}
