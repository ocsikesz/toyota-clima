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

    /** Ore de pornire luni–vineri, format "HH:MM", separate prin virgulă. */
    var times: String
        get() = str("times", "06:20, 14:30")
        set(v) = putStr("times", v)

    /** Ore de pornire sâmbătă–duminică. */
    var weekendTimes: String
        get() = str("weekend_times", "09:00")
        set(v) = putStr("weekend_times", v)

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
    /** La încălzire pornește și parbrizul + luneta (contra aburilor și gheții). La răcire niciodată. */
    var glassHeat: Boolean
        get() = sp.getBoolean("glass_heat", true)
        set(v) = sp.edit().putBoolean("glass_heat", v).apply()
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

    /** Titlul evenimentului din calendar pentru următoarea rulare; gol = program săptămânal. */
    var nextLabel: String
        get() = str("next_label")
        set(v) = putStr("next_label", v)

    // Evenimente din Google Calendar
    var eventsEnabled: Boolean
        get() = sp.getBoolean("events_enabled", false)
        set(v) = sp.edit().putBoolean("events_enabled", v).apply()

    /** Cuvântul care trebuie să apară în titlul evenimentului. */
    var eventKeyword: String
        get() = str("event_keyword", "mașina")
        set(v) = putStr("event_keyword", v)

    /** Cu câte minute înainte de ora evenimentului pornește clima. */
    var eventLeadMinutes: Int
        get() = sp.getInt("event_lead", 5)
        set(v) = sp.edit().putInt("event_lead", v).apply()

    // Excepții pe date concrete, format "yyyy-MM-dd": zile pornite / oprite manual din calendar.
    private fun dateSet(key: String): Set<String> =
        str(key).split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun putDateSet(key: String, v: Set<String>) {
        val today = dateKey(Calendar.getInstance())
        putStr(key, v.filter { it >= today }.sorted().joinToString(","))
    }

    var onDates: Set<String>
        get() = dateSet("on_dates")
        set(v) = putDateSet("on_dates", v)
    var offDates: Set<String>
        get() = dateSet("off_dates")
        set(v) = putDateSet("off_dates", v)

    /** Pornește clima în ziua asta? Excepțiile au prioritate față de zilele săptămânii. */
    fun isActiveOn(cal: Calendar): Boolean {
        val k = dateKey(cal)
        if (k in offDates) return false
        if (k in onDates) return true
        return cal.get(Calendar.DAY_OF_WEEK) in days
    }

    fun isException(cal: Calendar): Boolean {
        val k = dateKey(cal)
        return k in onDates || k in offDates
    }

    /** Inversează o singură zi din calendar, fără să schimbe programul săptămânal. */
    fun toggleDate(cal: Calendar) {
        val k = dateKey(cal)
        val active = isActiveOn(cal)
        val weekly = cal.get(Calendar.DAY_OF_WEEK) in days
        val on = onDates.toMutableSet().apply { remove(k) }
        val off = offDates.toMutableSet().apply { remove(k) }
        if (active && weekly) off.add(k)
        if (!active && !weekly) on.add(k)
        onDates = on
        offDates = off
    }

    /** Ore parsate luni–vineri: listă de (oră, minut). */
    fun parsedTimes(): List<Pair<Int, Int>> = parseTimes(times)

    /** Ore parsate sâmbătă–duminică. */
    fun parsedWeekendTimes(): List<Pair<Int, Int>> = parseTimes(weekendTimes)

    fun isWeekend(cal: Calendar): Boolean =
        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    /** Orele care se aplică într-o zi anume: de weekend sau de luni–vineri. */
    fun timesFor(cal: Calendar): List<Pair<Int, Int>> =
        if (isWeekend(cal)) parsedWeekendTimes() else parsedTimes()

    fun hasAnyTimes(): Boolean = parsedTimes().isNotEmpty() || parsedWeekendTimes().isNotEmpty()

    private fun parseTimes(s: String): List<Pair<Int, Int>> = s.split(',', ';', ' ')
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
        val DAY_LABELS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

        fun dateKey(cal: Calendar): String = String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
