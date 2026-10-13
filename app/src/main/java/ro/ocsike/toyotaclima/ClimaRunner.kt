package ro.ocsike.toyotaclima

import android.content.Context
import org.json.JSONObject
import java.util.Locale

object ClimaRunner {

    enum class Mode { SCHEDULED, TEST, START_NOW, STOP }

    private const val WEATHER_TIMEOUT = 6_000

    fun outsideTemperature(prefs: Prefs): Double = weather(prefs).first

    /**
     * Temperatura de afară și o notă despre sursă (gol = Open-Meteo, normal).
     * Ordine: Open-Meteo (2 încercări) → MET Norway → prognoza orară salvată la o citire anterioară
     * → ultima temperatură primită, dacă are sub 6 ore → citirea de la aceeași oră din zilele trecute.
     * Timpi de așteptare scurți, ca rularea de la alarmă să încapă în timpul pe care îl dă Android.
     */
    fun weather(prefs: Prefs): Pair<Double, String> {
        val errors = mutableListOf<String>()
        for (delay in longArrayOf(0, 3_000)) {
            if (delay > 0) Thread.sleep(delay)
            try {
                val t = openMeteo(prefs)
                prefs.cacheTemperature(t)
                return t to ""
            } catch (e: Exception) {
                errors += "Open-Meteo: ${e.message}"
            }
        }
        try {
            val t = metNorway(prefs)
            prefs.cacheTemperature(t)
            return t to " (weather from MET Norway, Open-Meteo was down)"
        } catch (e: Exception) {
            errors += "MET Norway: ${e.message}"
        }
        val forecast = prefs.forecastAt(System.currentTimeMillis())
        if (forecast != null) {
            return forecast to " (weather services down, using the saved forecast for this hour)"
        }
        val cached = prefs.cachedTemperature(maxAgeMs = 6 * 3_600_000L)
        if (cached != null) {
            val minutes = (System.currentTimeMillis() - cached.second) / 60_000
            return cached.first to " (weather services down, using the reading from $minutes min ago)"
        }
        val sameTime = prefs.sameTimeOfDayTemperature(System.currentTimeMillis())
        if (sameTime != null) {
            val days = ((System.currentTimeMillis() - sameTime.second + 12 * 3_600_000L) / 86_400_000L).coerceAtLeast(1)
            val at = java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(sameTime.second))
            val whenText = if (days == 1L) "yesterday at $at" else "$days days ago at $at"
            return sameTime.first to " (weather services down, using the reading from $whenText)"
        }
        throw Exception("No weather available. " + errors.distinct().joinToString("; "))
    }

    private fun openMeteo(prefs: Prefs): Double {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m" +
                "&hourly=temperature_2m&forecast_days=2&timeformat=unixtime",
            prefs.latitude, prefs.longitude,
        )
        val resp = Http.request("GET", url, connectTimeoutMs = WEATHER_TIMEOUT, readTimeoutMs = WEATHER_TIMEOUT)
        if (resp.code != 200) throw Exception("returned ${resp.code}")
        val json = JSONObject(resp.body)
        try {
            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            prefs.cacheForecast((0 until times.length()).map { times.getLong(it) * 1000 to temps.getDouble(it) })
        } catch (e: Exception) { /* prognoza e doar rezervă */ }
        return json.getJSONObject("current").getDouble("temperature_2m")
    }

    /** Serviciul meteo norvegian: gratuit, fără cheie, cere un User-Agent care identifică aplicația. */
    private fun metNorway(prefs: Prefs): Double {
        val url = String.format(
            Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
            prefs.latitude, prefs.longitude,
        )
        val resp = Http.request(
            "GET", url,
            mapOf("User-Agent" to "ToyClimate/1.0 github.com/ocsikesz/toyota-clima"),
            connectTimeoutMs = WEATHER_TIMEOUT,
            readTimeoutMs = WEATHER_TIMEOUT,
        )
        if (resp.code != 200) throw Exception("returned ${resp.code}")
        val series = JSONObject(resp.body).getJSONObject("properties").getJSONArray("timeseries")
        fun tempAt(i: Int) = series.getJSONObject(i).getJSONObject("data").getJSONObject("instant")
            .getJSONObject("details").getDouble("air_temperature")
        try {
            prefs.cacheForecast((0 until minOf(series.length(), 48)).map { i ->
                java.time.Instant.parse(series.getJSONObject(i).getString("time")).toEpochMilli() to tempAt(i)
            })
        } catch (e: Exception) { /* prognoza e doar rezervă */ }
        return tempAt(0)
    }

    /** Construiește comanda în funcție de temperatură; null = nu pornim nimic. */
    fun decide(temp: Double, prefs: Prefs): Pair<String, JSONObject?> {
        val t = String.format(Locale.US, "%.1f", temp)
        return when {
            temp < prefs.heatBelow -> {
                val defrost = if (prefs.glassHeat) "on" else "off"
                val glass = if (prefs.glassHeat) ", with heated windshield and rear window" else ""
                "Outside $t°C → heating to ${fmt(prefs.heatTarget)}°C$glass" to
                    startBody(prefs.heatTarget, prefs.duration, defrost)
            }
            temp > prefs.coolAbove -> {
                "Outside $t°C → cooling to ${fmt(prefs.coolTarget)}°C" to
                    startBody(prefs.coolTarget, prefs.duration, "off")
            }
            else -> "Outside $t°C, between ${fmt(prefs.heatBelow)} and ${fmt(prefs.coolAbove)}°C → nothing to start" to null
        }
    }

    private fun fmt(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

    private fun startBody(target: Double, duration: Int, defrost: String): JSONObject =
        JSONObject()
            .put("command", "start")
            .put("duration", duration)
            .put("temperature", JSONObject().put("unit", "C").put("value", target))
            .put("heatingOptions", JSONObject().put("frontDefroster", defrost).put("rearDefogger", defrost))
            .put("saveSettings", false)

    private fun explainCode(code: String?): String = when (code) {
        ToyotaApi.RETURN_OK -> "accepted"
        null -> "no response code"
        else -> "rejected (code $code). Check that the car is locked, the doors are closed, the key isn't inside and the climate isn't already running"
    }

    /** Rulează o acțiune și returnează mesajul pentru utilizator. Blochează: apelează din fundal. */
    fun run(context: Context, mode: Mode): String {
        val prefs = Prefs(context)
        val api = ToyotaApi(prefs)
        val message = try {
            when (mode) {
                Mode.STOP -> {
                    val vin = api.resolveVin()
                    val code = api.climateControl(vin, JSONObject().put("command", "stop"))
                    "Stop climate: ${explainCode(code)}"
                }
                Mode.START_NOW -> {
                    // Pornire forțată: răcire dacă e cald afară, altfel încălzire
                    val temp = try { outsideTemperature(prefs) } catch (e: Exception) { null }
                    val cool = temp != null && temp > prefs.coolAbove
                    val target = if (cool) prefs.coolTarget else prefs.heatTarget
                    val defrost = if (!cool && prefs.glassHeat) "on" else "off"
                    val vin = api.resolveVin()
                    val code = api.climateControl(vin, startBody(target, prefs.duration, defrost))
                    "Manual start at ${fmt(target)}°C: ${explainCode(code)}"
                }
                Mode.TEST, Mode.SCHEDULED -> {
                    val (temp, source) = weather(prefs)
                    val (decision, body) = decide(temp, prefs)
                    val text = decision + source
                    when {
                        mode == Mode.TEST -> {
                            // Test: verifică și login-ul Toyota, fără să trimită comanda
                            val vin = api.resolveVin()
                            "TEST: $text. Toyota login OK, car …${vin.takeLast(6)}. No command sent."
                        }
                        body == null -> text
                        else -> {
                            val vin = api.resolveVin()
                            "$text: ${explainCode(api.climateControl(vin, body))}"
                        }
                    }
                }
            }
        } catch (e: ToyotaException) {
            "Error: ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.javaClass.simpleName}: ${e.message}"
        }
        prefs.log(message)
        return message
    }
}
