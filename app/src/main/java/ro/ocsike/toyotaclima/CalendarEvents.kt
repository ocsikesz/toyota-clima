package ro.ocsike.toyotaclima

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

    /** Un eveniment din ziua aleasă, cu sau fără cuvântul care pornește clima. */
    data class DayEvent(
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val startsClimate: Boolean,
    )

    /** Evenimentul pornește clima? (evenimente active, cuvânt setat și prezent în titlu) */
    fun matches(prefs: Prefs, title: String): Boolean {
        val keyword = normalize(prefs.eventKeyword.trim())
        return prefs.eventsEnabled && keyword.isNotEmpty() && normalize(title).contains(keyword)
    }

    /** Toate evenimentele dintr-o zi, inclusiv cele de o zi întreagă. Gol fără permisiune. */
    fun eventsOn(context: Context, prefs: Prefs, day: Calendar): List<DayEvent> {
        if (!hasPermission(context)) return emptyList()
        val start = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = (day.clone() as Calendar).apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val key = Prefs.dateKey(day)

        // Interval lărgit cu o zi în fiecare parte: evenimentele de o zi întreagă sunt stocate în UTC.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 86_400_000L)
            ContentUris.appendId(it, end + 86_400_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
        )
        val utc = TimeZone.getTimeZone("UTC")
        val result = mutableListOf<DayEvent>()
        try {
            context.contentResolver.query(
                uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(0)
                    val finish = c.getLong(1)
                    val title = (c.getString(2) ?: "").trim().ifEmpty { "(no title)" }
                    val allDay = c.getInt(3) == 1
                    val inDay = if (allDay) {
                        // O zi întreagă acoperă intervalul [begin, finish) în date UTC.
                        val first = Prefs.dateKey(Calendar.getInstance(utc).apply { timeInMillis = begin })
                        val last = Prefs.dateKey(Calendar.getInstance(utc).apply { timeInMillis = finish - 1 })
                        key in first..last
                    } else {
                        begin < end && finish > start
                    }
                    if (!inDay) continue
                    result += DayEvent(title, begin, finish, allDay, !allDay && matches(prefs, title))
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return result.sortedWith(compareBy({ !it.allDay }, { it.begin }))
    }

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
