package ro.ocsike.toyotaclima

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Un program repetitiv: zilele săptămânii, orele și dacă e activ. */
data class Rule(val id: Long, val days: Set<Int>, val times: List<Pair<Int, Int>>, val enabled: Boolean)

/** O pornire o singură dată: data "yyyy-MM-dd" și ora. */
data class OneOff(val id: Long, val date: String, val time: Pair<Int, Int>)

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

    /**
     * Programe repetitive: fiecare are zilele lui (constante Calendar, MONDAY=2 ... SUNDAY=1),
     * orele lui și un comutator. Salvate ca JSON. La prima citire se migrează vechile
     * „days” + „times” (+ „weekend_times”) într-unul sau două programe.
     */
    var rules: List<Rule>
        get() {
            val raw = sp.getString("rules", null) ?: return migrateRules().also { rules = it }
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val d = o.getJSONArray("days")
                    Rule(
                        id = o.getLong("id"),
                        days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                        times = parseTimes(o.optString("times")),
                        enabled = o.optBoolean("on", true),
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("days", JSONArray(r.days.sorted()))
                        .put("times", formatTimes(r.times))
                        .put("on", r.enabled),
                )
            }
            putStr("rules", arr.toString())
        }

    private fun migrateRules(): List<Rule> {
        val oldDays = str("days", "2,3,4,5,6").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val weekDays = oldDays.filter { it in Calendar.MONDAY..Calendar.FRIDAY }.toSet()
        val weekendDays = oldDays.filter { it == Calendar.SATURDAY || it == Calendar.SUNDAY }.toSet()
        val list = mutableListOf<Rule>()
        if (weekDays.isNotEmpty()) list += Rule(1, weekDays, parseTimes(str("times", "06:20, 14:30")), true)
        if (weekendDays.isNotEmpty()) list += Rule(2, weekendDays, parseTimes(str("weekend_times", "09:00")), true)
        if (list.isEmpty()) {
            list += Rule(1, (Calendar.MONDAY..Calendar.FRIDAY).toSet(), listOf(6 to 20, 14 to 30), true)
        }
        return list
    }

    /** Porniri o singură dată (dată + oră). Cele din zilele trecute se șterg automat. */
    var oneOffs: List<OneOff>
        get() = try {
            val arr = JSONArray(str("one_offs", "[]"))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val t = parseTimes(o.optString("time")).firstOrNull() ?: return@mapNotNull null
                OneOff(o.getLong("id"), o.getString("date"), t)
            }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) {
            val today = dateKey(Calendar.getInstance())
            val arr = JSONArray()
            v.filter { it.date >= today }.sortedWith(compareBy({ it.date }, { it.time.first * 60 + it.time.second }))
                .forEach { o ->
                    arr.put(JSONObject().put("id", o.id).put("date", o.date).put("time", formatTimes(listOf(o.time))))
                }
            putStr("one_offs", arr.toString())
        }

    fun newId(): Long = System.currentTimeMillis()

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

    /** Ultima temperatură primită, de rezervă când serviciile meteo nu răspund. */
    fun cacheTemperature(t: Double) {
        sp.edit().putString("last_temp", t.toString()).putLong("last_temp_at", System.currentTimeMillis()).apply()
    }

    /** Prognoza orară (moment în ms, temperatură), salvată la fiecare citire meteo reușită. */
    fun cacheForecast(points: List<Pair<Long, Double>>) {
        if (points.isEmpty()) return
        putStr("forecast", points.joinToString(",") { (t, v) -> "$t:$v" })
    }

    /** Temperatura din prognoza salvată pentru momentul [at], dacă există un punct la cel mult 90 de minute. */
    fun forecastAt(at: Long): Double? = str("forecast").split(',')
        .mapNotNull { p ->
            val (t, v) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val time = t.toLongOrNull() ?: return@mapNotNull null
            val temp = v.toDoubleOrNull() ?: return@mapNotNull null
            time to temp
        }
        .minByOrNull { kotlin.math.abs(it.first - at) }
        ?.takeIf { kotlin.math.abs(it.first - at) <= 90 * 60_000L }
        ?.second

    /** (temperatură, momentul citirii) dacă e mai nouă de [maxAgeMs], altfel null. */
    fun cachedTemperature(maxAgeMs: Long): Pair<Double, Long>? {
        val t = sp.getString("last_temp", null)?.toDoubleOrNull() ?: return null
        val at = sp.getLong("last_temp_at", 0)
        return if (System.currentTimeMillis() - at <= maxAgeMs) t to at else null
    }

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

    // Zile sărite: data "yyyy-MM-dd" în care programele repetitive nu pornesc (ex. concediu).
    var offDates: Set<String>
        get() = str("off_dates").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        set(v) {
            val today = dateKey(Calendar.getInstance())
            putStr("off_dates", v.filter { it >= today }.sorted().joinToString(","))
        }

    fun isSkipped(cal: Calendar): Boolean = dateKey(cal) in offDates

    fun setSkipped(cal: Calendar, skip: Boolean) {
        val k = dateKey(cal)
        offDates = if (skip) offDates + k else offDates - k
    }

    /** O pornire într-o zi anume și de unde vine (program sau pornire unică). */
    data class Start(val time: Pair<Int, Int>, val label: String, val oneOffId: Long? = null)

    /** Programele active care se aplică în ziua săptămânii respective. */
    fun rulesOn(cal: Calendar): List<Rule> =
        rules.filter { it.enabled && cal.get(Calendar.DAY_OF_WEEK) in it.days && it.times.isNotEmpty() }

    /** Toate pornirile dintr-o zi: programele (dacă ziua nu e sărită) + pornirile unice. */
    fun startsOn(cal: Calendar): List<Start> {
        val k = dateKey(cal)
        val list = mutableListOf<Start>()
        if (k !in offDates) {
            rulesOn(cal).forEach { r -> r.times.forEach { list += Start(it, daysLabel(r.days)) } }
        }
        oneOffs.filter { it.date == k }.forEach { list += Start(it.time, "One-time start", it.id) }
        return list.distinctBy { it.time }.sortedBy { it.time.first * 60 + it.time.second }
    }

    fun timesOn(cal: Calendar): List<Pair<Int, Int>> = startsOn(cal).map { it.time }

    /** Pornește clima în ziua asta? */
    fun isActiveOn(cal: Calendar): Boolean = startsOn(cal).isNotEmpty()

    /** Ziua diferă de programul obișnuit: sărită sau cu pornire unică. */
    fun isException(cal: Calendar): Boolean {
        val k = dateKey(cal)
        return (k in offDates && rulesOn(cal).isNotEmpty()) || oneOffs.any { it.date == k }
    }

    fun hasAnyTimes(): Boolean =
        rules.any { it.enabled && it.days.isNotEmpty() && it.times.isNotEmpty() } || oneOffs.isNotEmpty()

    private fun formatTimes(list: List<Pair<Int, Int>>): String =
        list.joinToString(", ") { (h, m) -> String.format(Locale.US, "%02d:%02d", h, m) }

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
        private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        /** „Monday to Friday”, „Weekend”, „Every day” sau „Tue, Thu”. */
        fun daysLabel(days: Set<Int>): String = when {
            days.isEmpty() -> "No days selected"
            days.size == 7 -> "Every day"
            days == (Calendar.MONDAY..Calendar.FRIDAY).toSet() -> "Monday to Friday"
            days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> "Weekend"
            else -> DAY_ORDER.filter { it in days }.joinToString(", ") { DAY_NAMES[DAY_ORDER.indexOf(it)] }
        }

        fun dateKey(cal: Calendar): String = String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
    }
}
