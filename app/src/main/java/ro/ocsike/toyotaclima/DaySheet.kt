package ro.ocsike.toyotaclima

import android.app.Activity
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Lista unei zile, deschisă de jos când atingi o zi în calendar:
 * pornirile de clima, evenimentele din calendar, jurnalul (pentru zile trecute)
 * și comutatorul care pornește/oprește doar ziua respectivă.
 */
class DaySheet(
    private val activity: Activity,
    private val prefs: Prefs,
    private val day: Calendar,
    private val onChanged: () -> Unit,
) {
    private val dp = activity.resources.displayMetrics.density
    private fun px(v: Number) = (v.toFloat() * dp).toInt()

    private val ink = activity.getColor(R.color.ink)
    private val muted = activity.getColor(R.color.muted)
    private val line = activity.getColor(R.color.line)
    private val heat = activity.getColor(R.color.heat)
    private val surface = activity.getColor(R.color.surface)

    private val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val en = Locale.ENGLISH
    private val hm = SimpleDateFormat("HH:mm", en)

    private val dialog = Dialog(activity)
    private val body = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(20), px(10), px(20), px(28))
    }

    private val key = Prefs.dateKey(day)
    private val todayKey = Prefs.dateKey(Calendar.getInstance())
    private val past = key < todayKey

    fun show() {
        render()
        val scroll = ScrollView(activity).apply { addView(body) }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(scroll)
        dialog.window?.apply {
            val r = 24 * dp
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            })
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.show()
    }

    // ---------- Conținut ----------

    private fun render() {
        body.removeAllViews()

        // Mâner de tras, doar ca semn vizual că e o foaie care se închide în jos
        val handle = View(activity).apply { background = rounded(line, 3) }
        body.addView(handle, LinearLayout.LayoutParams(px(40), px(5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = px(14)
        })

        body.addView(text(title(), 24, ink).apply { typeface = medium })
        renderClimate()
        renderCalendar()
        if (key <= todayKey) renderLog()
        if (!past) renderSwitch()
    }

    private fun title(): String {
        val full = SimpleDateFormat("EEEE, MMMM d", en).format(day.time)
        val tomorrow = Prefs.dateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) })
        return when (key) {
            todayKey -> "Today, ${full.substringAfter(", ")}"
            tomorrow -> "Tomorrow, ${full.substringAfter(", ")}"
            else -> full
        }
    }

    private data class Item(val time: Long, val what: String, val note: String?, val oneOffId: Long? = null)

    private fun millisAt(t: Pair<Int, Int>): Long = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, t.first)
        set(Calendar.MINUTE, t.second)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Pornirile de clima din ziua aceea: programe, porniri unice și evenimente. */
    private fun renderClimate() {
        section("Climate")
        val now = System.currentTimeMillis()
        val scheduled = prefs.startsOn(day)
        val items = mutableListOf<Item>()
        scheduled.forEach { s -> items += Item(millisAt(s.time), s.label, null, s.oneOffId) }
        val scheduledMillis = scheduled.map { millisAt(it.time) }

        CalendarEvents.upcoming(activity, prefs, startOfDay(), days = 1)
            .filter { Prefs.dateKey(Calendar.getInstance().apply { timeInMillis = it.time }) == key }
            .forEach { tr ->
                val near = scheduledMillis.firstOrNull { abs(it - tr.time) < 15 * 60_000L }
                items += Item(
                    tr.time,
                    "For \"${tr.title}\" at ${hm.format(Date(tr.eventStart))}",
                    near?.let { "Already covered by the ${hm.format(Date(it))} start" },
                )
            }

        if (!prefs.enabled) {
            body.addView(text("The schedule is off. Turn it on from the switch on Home.", 15, muted))
        }
        if (prefs.isSkipped(day) && prefs.rulesOn(day).isNotEmpty()) {
            body.addView(text("Your schedules are skipped on this day.", 15, muted).apply {
                setPadding(0, px(4), 0, 0)
            })
        }
        if (items.isEmpty()) {
            body.addView(text("The climate doesn't start on this day.", 15, muted).apply {
                setPadding(0, px(4), 0, 0)
            })
        }
        items.sortedBy { it.time }.forEach { s ->
            val done = s.time < now
            val dimmed = done || s.note != null || !prefs.enabled
            val removable = s.oneOffId != null && !done
            body.addView(
                row(
                    hm.format(Date(s.time)), s.what, s.note, marker = true, dimmed = dimmed,
                    actionLabel = if (removable) "Remove" else null,
                    onAction = {
                        prefs.oneOffs = prefs.oneOffs.filter { it.id != s.oneOffId }
                        onChanged()
                        render()
                    },
                ),
            )
        }
        if (!past) {
            body.addView(pill("+ Add a one-time start") {
                TimePickerDialog(activity, { _, h, m ->
                    prefs.oneOffs = prefs.oneOffs + OneOff(prefs.newId(), key, h to m)
                    onChanged()
                    render()
                }, 8, 0, true).show()
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
        }
    }

    /** Toate evenimentele din calendar ale zilei, chiar și cele care nu pornesc clima. */
    private fun renderCalendar() {
        section("Calendar")
        if (!CalendarEvents.hasPermission(activity)) {
            body.addView(
                text("Allow calendar access in Settings → Calendar events to see your events here.", 15, muted),
            )
            return
        }
        val events = CalendarEvents.eventsOn(activity, prefs, day)
        if (events.isEmpty()) {
            body.addView(text("No events.", 15, muted).apply { setPadding(0, px(4), 0, 0) })
            return
        }
        events.forEach { e ->
            val time = if (e.allDay) "All day" else "${hm.format(Date(e.begin))}–${hm.format(Date(e.end))}"
            val note = if (e.startsClimate) "Starts the climate" else null
            body.addView(row(time, e.title, note, marker = e.startsClimate, dimmed = false, noteColor = heat))
        }
    }

    /** Ce s-a întâmplat în ziua aceea, din jurnal. */
    private fun renderLog() {
        val prefix = SimpleDateFormat("dd.MM", en).format(day.time) + " "
        val lines = prefs.logText().lineSequence().filter { it.startsWith(prefix) }.toList()
        if (lines.isEmpty() && !past) return
        section("Log")
        if (lines.isEmpty()) {
            body.addView(text("Nothing was recorded on this day.", 15, muted))
            return
        }
        lines.reversed().forEach { l ->
            val rest = l.removePrefix(prefix)
            val time = rest.take(5)
            val msg = rest.drop(8).trim()
            body.addView(row(time, msg, null, marker = false, dimmed = false))
        }
    }

    /** Sare programele repetitive doar în ziua aceasta (ex. concediu). Apare doar dacă există programe. */
    private fun renderSwitch() {
        val rules = prefs.rulesOn(day)
        if (rules.isEmpty()) return
        body.addView(View(activity).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, px(1)).apply {
            topMargin = px(24)
            bottomMargin = px(12)
        })
        val r = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text("Run my schedules this day", 16, ink).apply { typeface = medium })
        val sub = if (prefs.isSkipped(day)) {
            "Skipped for this date only"
        } else {
            rules.joinToString("; ") { Prefs.daysLabel(it.days) }
        }
        labels.addView(text(sub, 13, muted))
        r.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(Switch(activity).apply {
            isChecked = !prefs.isSkipped(day)
            thumbTintList = tint(heat, 0xFFBFC8D4.toInt())
            trackTintList = tint(heat, line)
            contentDescription = "Run my schedules this day"
            setOnCheckedChangeListener { _, checked ->
                prefs.setSkipped(day, !checked)
                onChanged()
                render()
            }
        })
        body.addView(r)
    }

    // ---------- Componente ----------

    private fun startOfDay(): Long = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun section(s: String) {
        body.addView(text(s, 15, muted).apply {
            typeface = medium
            setPadding(0, px(20), 0, px(4))
        })
    }

    /** Un rând: ora în stânga (condensat), descrierea și o notă opțională în dreapta. */
    private fun row(
        time: String,
        what: String,
        note: String?,
        marker: Boolean,
        dimmed: Boolean,
        noteColor: Int = muted,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ): View {
        val r = LinearLayout(activity).apply {
            setPadding(0, px(8), 0, px(8))
            alpha = if (dimmed) 0.5f else 1f
        }
        val dot = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (marker) setColor(heat) else setStroke(px(1.5f), line)
            }
        }
        r.addView(dot, LinearLayout.LayoutParams(px(10), px(10)).apply {
            topMargin = px(7)
            rightMargin = px(12)
        })
        r.addView(text(time, 17, ink).apply { typeface = condensed }, LinearLayout.LayoutParams(px(96), -2))
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(what, 15, ink).apply { setLineSpacing(0f, 1.1f) })
        if (note != null) col.addView(text(note, 13, noteColor))
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (actionLabel != null && onAction != null) {
            r.addView(text(actionLabel, 14, ink).apply {
                typeface = medium
                setPadding(px(10), px(2), 0, px(2))
                isClickable = true
                isFocusable = true
                setOnClickListener { onAction() }
            })
        }
        return r
    }

    private fun pill(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 15f
        typeface = medium
        setTextColor(ink)
        setPadding(px(18), px(10), px(18), px(10))
        background = RippleDrawable(
            ColorStateList.valueOf(0x33808080),
            GradientDrawable().apply {
                cornerRadius = 999 * dp
                setStroke(px(1.5f), line)
            },
            null,
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun text(s: String, sizeSp: Int, color: Int) = TextView(activity).apply {
        text = s
        textSize = sizeSp.toFloat()
        setTextColor(color)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * dp
    }

    private fun tint(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(on, off),
    )
}
