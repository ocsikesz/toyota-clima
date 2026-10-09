package ro.ocsike.toyotaclima

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("toyota_clima", Context.MODE_PRIVATE)

    private fun str(key: String, def: String = "") = sp.getString(key, def) ?: def
    private fun putStr(key: String, value: String) = sp.edit().putString(key, value).apply()
    private fun dbl(key: String, def: Double) = str(key, def.toString()).toDoubleOrNull() ?: def
    private fun putDbl(key: String, value: Double) = putStr(key, value.toString())

    // Cont
    var username: String
        get() = str("username")
        set(v) = putStr("username", v)
    var password: String
        get() = str("password")
        set(v) = putStr("password", v)
    var vin: String
        get() = str("vin")
        set(v) = putStr("vin", v)

    // Token
    var accessToken: String
        get() = str("access_token")
        set(v) = putStr("access_token", v)
    var refreshToken: String
        get() = str("refresh_token")
        set(v) = putStr("refresh_token", v)
    var uuid: String
        get() = str("uuid")
        set(v) = putStr("uuid", v)
    var tokenExpiresAt: Long
        get() = sp.getLong("token_expires_at", 0)
        set(v) = sp.edit().putLong("token_expires_at", v).apply()

    // Programare
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Ore de pornire, format "HH:MM", separate prin virgulă. */
    var times: String
        get() = str("times", "06:20, 14:30")
        set(v) = putStr("times", v)

    /** Zile active, constante Calendar (MONDAY=2 ... SUNDAY=1). */
    var days: Set<Int>
        get() = str("days", "2,3,4,5,6").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        set(v) = putStr("days", v.sorted().joinToString(","))

    // Praguri
    var heatBelow: Double
        get() = dbl("heat_below", 12.0)
        set(v) = putDbl("heat_below", v)
    var coolAbove: Double
        get() = dbl("cool_above", 24.0)
        set(v) = putDbl("cool_above", v)
    var heatTarget: Double
        get() = dbl("heat_target", 22.0)
        set(v) = putDbl("heat_target", v)
    var coolTarget: Double
        get() = dbl("cool_target", 20.0)
        set(v) = putDbl("cool_target", v)
    var defrostBelow: Double
        get() = dbl("defrost_below", 3.0)
        set(v) = putDbl("defrost_below", v)
    var duration: Int
        get() = sp.getInt("duration", 10)
        set(v) = sp.edit().putInt("duration", v).apply()

    // Locație pentru vreme (implicit Târgu Mureș)
    var latitude: Double
        get() = dbl("latitude", 46.54)
        set(v) = putDbl("latitude", v)
    var longitude: Double
        get() = dbl("longitude", 24.56)
        set(v) = putDbl("longitude", v)

    var nextRun: Long
        get() = sp.getLong("next_run", 0)
        set(v) = sp.edit().putLong("next_run", v).apply()

    /** Ore parsate: listă de (oră, minut). */
    fun parsedTimes(): List<Pair<Int, Int>> = times.split(',', ';', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { t ->
            val p = t.replace('.', ':').split(':')
            val h = p.getOrNull(0)?.toIntOrNull()
            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
            if (h != null && h in 0..23 && m in 0..59) h to m else null
        }

    // Jurnal (ultimele 40 de intrări)
    fun log(message: String) {
        val stamp = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
        synchronized(Prefs::class.java) {
            val lines = str("log").split('\n').filter { it.isNotBlank() }.toMutableList()
            lines.add(0, "$stamp  $message")
            putStr("log", lines.take(40).joinToString("\n"))
        }
    }

    fun logText(): String = str("log")

    fun clearLog() = putStr("log", "")

    companion object {
        val DAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
        )
        val DAY_LABELS = listOf("L", "Ma", "Mi", "J", "V", "S", "D")
    }
}
