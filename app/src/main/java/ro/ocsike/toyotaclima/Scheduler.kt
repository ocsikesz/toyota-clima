package ro.ocsike.toyotaclima

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    private const val REQUEST_CODE = 1001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Următorul moment de rulare după [from], sau null dacă nu e nimic programat. */
    fun nextTrigger(prefs: Prefs, from: Long = System.currentTimeMillis()): Long? {
        if (!prefs.hasAnyTimes()) return null
        // Caută până la ~2 luni înainte, ca să treacă și peste concedii marcate în calendar.
        for (offset in 0..62) {
            val day = Calendar.getInstance()
            day.timeInMillis = from
            day.add(Calendar.DAY_OF_YEAR, offset)
            if (!prefs.isActiveOn(day)) continue
            val times = prefs.timesFor(day)
            var best: Long? = null
            for ((h, m) in times) {
                val cal = day.clone() as Calendar
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val t = cal.timeInMillis
                if (t <= from + 30_000) continue
                if (best == null || t < best) best = t
            }
            if (best != null) return best
        }
        return null
    }

    /** Pornirile din programul săptămânal într-o zi anume (pentru evitarea dublurilor). */
    fun weeklyTimesOn(prefs: Prefs, dayMillis: Long): List<Long> {
        val day = Calendar.getInstance().apply { timeInMillis = dayMillis }
        if (!prefs.isActiveOn(day)) return emptyList()
        return prefs.timesFor(day).map { (h, m) ->
            (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    /**
     * Următoarea pornire dintr-un eveniment din calendar. Sare peste evenimentele aflate
     * la mai puțin de 15 minute de o pornire din program, ca să nu pornească de două ori.
     */
    fun nextEventTrigger(context: Context, prefs: Prefs, from: Long = System.currentTimeMillis()): CalendarEvents.Trigger? {
        val window = 15 * 60_000L
        return CalendarEvents.upcoming(context, prefs, from).firstOrNull { tr ->
            tr.time > from + 30_000 &&
                weeklyTimesOn(prefs, tr.time).none { kotlin.math.abs(it - tr.time) < window }
        }
    }

    // Reverificare periodică a calendarului, pentru evenimente adăugate între timp.
    private const val REFRESH_CODE = 1002
    const val ACTION_REFRESH = "ro.ocsike.toyotaclima.REFRESH"

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REFRESH_CODE,
        Intent(context, BootReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun updateRefresh(context: Context, prefs: Prefs) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = refreshIntent(context)
        if (prefs.enabled && prefs.eventsEnabled) {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
                AlarmManager.INTERVAL_HALF_HOUR, pi,
            )
        } else {
            am.cancel(pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** (Re)programează alarma pentru următoarea rulare. Returnează momentul sau null. */
    fun schedule(context: Context): Long? {
        val prefs = Prefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)
        updateRefresh(context, prefs)
        prefs.nextLabel = ""
        if (!prefs.enabled) {
            prefs.nextRun = 0
            return null
        }
        val weekly = nextTrigger(prefs)
        val event = nextEventTrigger(context, prefs)
        val next = when {
            event != null && (weekly == null || event.time < weekly) -> {
                prefs.nextLabel = event.title
                event.time
            }
            else -> weekly
        }
        if (next == null) {
            prefs.nextRun = 0
            return null
        }
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
        prefs.nextRun = next
        return next
    }
}
