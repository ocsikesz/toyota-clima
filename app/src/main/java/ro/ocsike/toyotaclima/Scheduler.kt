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
        val times = prefs.parsedTimes()
        if (times.isEmpty()) return null
        // Caută până la ~2 luni înainte, ca să treacă și peste concedii marcate în calendar.
        for (offset in 0..62) {
            val day = Calendar.getInstance()
            day.timeInMillis = from
            day.add(Calendar.DAY_OF_YEAR, offset)
            if (!prefs.isActiveOn(day)) continue
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
        if (!prefs.enabled) {
            prefs.nextRun = 0
            return null
        }
        val next = nextTrigger(prefs)
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
