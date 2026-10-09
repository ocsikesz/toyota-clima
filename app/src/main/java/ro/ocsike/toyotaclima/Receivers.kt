package ro.ocsike.toyotaclima

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "ro.ocsike.toyotaclima.RUN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // Programează imediat următoarea rulare, ca un eșec să nu oprească lanțul.
        Scheduler.schedule(appContext)

        val pending = goAsync()
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ToyotaClima:run")
        wakeLock.acquire(55_000)
        Thread {
            try {
                val msg = ClimaRunner.run(appContext, ClimaRunner.Mode.SCHEDULED)
                Notify.show(appContext, msg)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.schedule(context.applicationContext)
    }
}
