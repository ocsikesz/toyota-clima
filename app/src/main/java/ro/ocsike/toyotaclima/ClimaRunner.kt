package ro.ocsike.toyotaclima

import android.content.Context
import org.json.JSONObject
import java.util.Locale

object ClimaRunner {

    enum class Mode { SCHEDULED, TEST, START_NOW, STOP }

    fun outsideTemperature(prefs: Prefs): Double {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m",
            prefs.latitude, prefs.longitude,
        )
        val resp = Http.request("GET", url)
        if (resp.code != 200) throw Exception("Open-Meteo a răspuns ${resp.code}")
        return JSONObject(resp.body).getJSONObject("current").getDouble("temperature_2m")
    }

    /** Construiește comanda în funcție de temperatură; null = nu pornim nimic. */
    fun decide(temp: Double, prefs: Prefs): Pair<String, JSONObject?> {
        val t = String.format(Locale.US, "%.1f", temp)
        return when {
            temp < prefs.heatBelow -> {
                val defrost = if (temp < prefs.defrostBelow) "on" else "off"
                "Afară $t°C → încălzire la ${fmt(prefs.heatTarget)}°C, degivrare $defrost" to
                    startBody(prefs.heatTarget, prefs.duration, defrost)
            }
            temp > prefs.coolAbove -> {
                "Afară $t°C → răcire la ${fmt(prefs.coolTarget)}°C" to
                    startBody(prefs.coolTarget, prefs.duration, "off")
            }
            else -> "Afară $t°C, între ${fmt(prefs.heatBelow)} și ${fmt(prefs.coolAbove)}°C → nu pornesc nimic" to null
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
        ToyotaApi.RETURN_OK -> "acceptată"
        null -> "fără cod de răspuns"
        else -> "respinsă (cod $code). Verifică: mașina încuiată, ușile închise, cheia nu e în mașină, climatizarea nu e deja pornită"
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
                    "Oprire climatizare: ${explainCode(code)}"
                }
                Mode.START_NOW -> {
                    // Pornire forțată: răcire dacă e cald afară, altfel încălzire
                    val temp = try { outsideTemperature(prefs) } catch (e: Exception) { null }
                    val cool = temp != null && temp > prefs.coolAbove
                    val target = if (cool) prefs.coolTarget else prefs.heatTarget
                    val defrost = if (temp != null && temp < prefs.defrostBelow) "on" else "off"
                    val vin = api.resolveVin()
                    val code = api.climateControl(vin, startBody(target, prefs.duration, defrost))
                    "Pornire manuală la ${fmt(target)}°C: ${explainCode(code)}"
                }
                Mode.TEST, Mode.SCHEDULED -> {
                    val temp = outsideTemperature(prefs)
                    val (text, body) = decide(temp, prefs)
                    when {
                        mode == Mode.TEST -> {
                            // Test: verifică și login-ul Toyota, fără să trimită comanda
                            val vin = api.resolveVin()
                            "TEST: $text. Login Toyota OK, mașina ${vin.takeLast(6)}. Nu am trimis comanda."
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
            "Eroare: ${e.message}"
        } catch (e: Exception) {
            "Eroare: ${e.javaClass.simpleName}: ${e.message}"
        }
        prefs.log(message)
        return message
    }
}
