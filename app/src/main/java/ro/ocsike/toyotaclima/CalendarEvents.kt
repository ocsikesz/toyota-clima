package ro.ocsike.toyotaclima

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.Normalizer
import java.util.Locale

/**
 * Evenimente din calendarele sincronizate pe telefon (Google Calendar inclus)
 * care conțin în titlu cuvântul ales. Clima pornește cu [Prefs.eventLeadMinutes] înainte.
 */
object CalendarEvents {

    data class Trigger(val time: Long, val title: String, val eventStart: Long)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Litere mici, fără diacritice: „Mașina” se potrivește cu „masina”. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)

    /** Porniri din evenimente între [from] și [from] + [days] zile, în ordine. */
    fun upcoming(context: Context, prefs: Prefs, from: Long, days: Int = 62): List<Trigger> {
        if (!prefs.eventsEnabled || !hasPermission(context)) return emptyList()
        val keyword = normalize(prefs.eventKeyword.trim())
        if (keyword.isEmpty()) return emptyList()
        val lead = prefs.eventLeadMinutes * 60_000L

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            // Include și evenimentele care încep imediat după [from], chiar dacă pornirea ar fi trecut.
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, from + days * 86_400_000L + lead)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
        )

        val result = mutableListOf<Trigger>()
        try {
            context.contentResolver.query(
                uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(0)
                    val title = c.getString(1) ?: ""
                    val allDay = c.getInt(2) == 1
                    if (allDay) continue // fără oră: nu știm când pleci
                    if (!normalize(title).contains(keyword)) continue
                    result += Trigger(begin - lead, title.trim(), begin)
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        return result.sortedBy { it.time }
    }
}
