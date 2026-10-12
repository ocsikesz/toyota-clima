package ro.ocsike.toyotaclima

import android.content.Context
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Starea ușilor și geamurilor + comenzi remote (geamuri, uși, avarie). */
object CarControl {

    enum class Command(val api: String, val label: String) {
        CLOSE_WINDOWS("power-window-close", "Închidere geamuri"),
        LOCK("door-lock", "Încuiere uși"),
        UNLOCK("door-unlock", "Descuiere uși"),
        FIND("hazard-on", "Semnalizare avarie"),
    }

    // ---------- Citirea stării ----------

    class Status(val json: JSONObject) {
        val updatedAt: OffsetDateTime? = json.optString("lastUpdateTimestamp", "").let {
            try { OffsetDateTime.parse(it) } catch (e: Exception) { null }
        }
        private val leftHandDrive = if (json.has("leftHandDrive") && !json.isNull("leftHandDrive"))
            json.optBoolean("leftHandDrive", true) else true

        private fun label(key: String): String = when (key) {
            "driver" -> "șofer"
            "passenger" -> "pasager"
            "rearLeft" -> if (leftHandDrive) "spate șofer" else "spate pasager"
            "rearRight" -> if (leftHandDrive) "spate pasager" else "spate șofer"
            "rearBack" -> "portbagaj"
            "hood" -> "capotă"
            else -> key
        }

        private fun state(obj: JSONObject?): String? =
            obj?.optString("status", "")?.lowercase()?.takeIf { it.isNotEmpty() }

        /** Geamuri deschise, după poziție. null = Toyota nu raportează geamurile. */
        val openWindows: List<String>? = json.optJSONObject("windows")?.let { w ->
            w.keys().asSequence().filter { state(w.optJSONObject(it)) == "open" }.map { label(it) }.toList()
        }

        private val doors = json.optJSONObject("doors")

        val unlockedDoors: List<String>? = doors?.let { d ->
            d.keys().asSequence()
                .filter { state(d.optJSONObject(it)?.optJSONObject("lockStatus")) == "unlocked" }
                .map { label(it) }.toList()
        }

        val openDoors: List<String>? = doors?.let { d ->
            d.keys().asSequence()
                .filter { state(d.optJSONObject(it)?.optJSONObject("openStatus")) == "open" }
                .map { label(it) }.toList()
        }

        /** Rezumat pe trei rânduri, pentru ecran și notificare. */
        fun summary(): String = buildString {
            append("Geamuri: ")
            append(
                when {
                    openWindows == null -> "nu se raportează"
                    openWindows.isEmpty() -> "toate închise ✓"
                    else -> "DESCHIS — ${openWindows.joinToString(", ")}"
                }
            )
            append("\nUși: ")
            append(
                when {
                    unlockedDoors == null -> "nu se raportează"
                    unlockedDoors.isEmpty() -> "încuiate ✓"
                    else -> "DESCUIATE — ${unlockedDoors.joinToString(", ")}"
                }
            )
            if (!openDoors.isNullOrEmpty()) append("\nDeschise: ${openDoors.joinToString(", ")}")
            append("\nActualizat: ")
            append(
                updatedAt?.atZoneSameInstant(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                    ?: "necunoscut"
            )
        }
    }

    /** Condiția care confirmă că o comandă a avut efect. null = nu se poate verifica din stare. */
    private fun confirmed(cmd: Command, s: Status): Boolean? = when (cmd) {
        Command.CLOSE_WINDOWS -> s.openWindows?.isEmpty()
        Command.LOCK -> s.unlockedDoors?.isEmpty()
        Command.UNLOCK -> s.unlockedDoors?.isNotEmpty()
        Command.FIND -> null
    }

    /**
     * Trezește mașina și așteaptă o stare nouă (max ~40 s).
     * Dacă mașina nu răspunde, întoarce ultima stare din cache.
     */
    private fun freshStatus(api: ToyotaApi, vin: String, after: OffsetDateTime?, until: ((Status) -> Boolean)? = null): Status {
        try { api.wakeStatus(vin) } catch (e: Exception) { /* nu toate mașinile acceptă trezirea */ }
        var last: Status? = null
        repeat(4) {
            Thread.sleep(10_000)
            val s = Status(api.vehicleStatus(vin))
            last = s
            val newer = after == null || (s.updatedAt != null && s.updatedAt.isAfter(after))
            if (newer && (until == null || until(s))) return s
        }
        return last ?: Status(api.vehicleStatus(vin))
    }

    /** Citește starea ușilor și geamurilor, trezind mașina. Blochează: apelează din fundal. */
    fun checkStatus(context: Context): String {
        val prefs = Prefs(context)
        val api = ToyotaApi(prefs)
        val message = try {
            val vin = api.resolveVin()
            val cached = Status(api.vehicleStatus(vin))
            val fresh = freshStatus(api, vin, cached.updatedAt)
            fresh.summary()
        } catch (e: ToyotaException) {
            "Eroare: ${e.message}"
        } catch (e: Exception) {
            "Eroare: ${e.javaClass.simpleName}: ${e.message}"
        }
        prefs.log("Stare mașină: ${message.replace('\n', ' · ')}")
        return message
    }

    /** Trimite o comandă și încearcă să confirme efectul din starea mașinii. Blochează. */
    fun send(context: Context, cmd: Command): String {
        val prefs = Prefs(context)
        val api = ToyotaApi(prefs)
        val message = try {
            val vin = api.resolveVin()
            val before = try { Status(api.vehicleStatus(vin)).updatedAt } catch (e: Exception) { null }
            val code = api.remoteCommand(vin, cmd.api)
            if (code != null && code != ToyotaApi.RETURN_OK) {
                "${cmd.label}: respinsă de Toyota (cod $code)."
            } else if (cmd == Command.FIND) {
                "${cmd.label}: comandă acceptată."
            } else {
                val s = freshStatus(api, vin, before) { confirmed(cmd, it) == true }
                when (confirmed(cmd, s)) {
                    true -> "${cmd.label}: confirmat ✓\n${s.summary()}"
                    false -> "${cmd.label}: acceptată, dar mașina încă nu raportează schimbarea.\n${s.summary()}"
                    null -> "${cmd.label}: acceptată (mașina nu raportează starea).\n${s.summary()}"
                }
            }
        } catch (e: ToyotaException) {
            "${cmd.label} — eroare: ${e.message}"
        } catch (e: Exception) {
            "${cmd.label} — eroare: ${e.javaClass.simpleName}: ${e.message}"
        }
        prefs.log(message.replace('\n', ' · '))
        return message
    }
}
