package ro.ocsike.toyotaclima

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * Calendar lunar desenat direct pe Canvas.
 * Zilele în care pornește clima sunt discuri chihlimbarii; un punct marchează excepțiile
 * puse manual. O atingere pe o zi (de azi încolo) o pornește sau o oprește doar pe ea.
 */
class MonthCalendarView(context: Context, private val prefs: Prefs) : View(context) {

    var onDayToggled: (() -> Unit)? = null

    /** Zile ("yyyy-MM-dd") cu evenimente din calendar care pornesc clima: desenate cu contur. */
    var eventDays: Set<String> = emptySet()
        set(v) {
            field = v
            invalidate()
        }

    private val month: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val dp = resources.displayMetrics.density
    private val ink = context.getColor(R.color.ink)
    private val muted = context.getColor(R.color.muted)
    private val heat = context.getColor(R.color.heat)
    private val onHeat = context.getColor(R.color.on_heat)

    private val headerH = 28 * dp
    private var cellW = 0f
    private var rowH = 0f

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12 * resources.displayMetrics.scaledDensity
        color = muted
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16 * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = ink
    }
    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * dp
        color = heat
    }

    // ---------- Navigare ----------

    private fun monthIndex(c: Calendar) = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)

    /** Se poate naviga de la luna curentă până la 11 luni înainte. */
    fun canShift(delta: Int): Boolean {
        val diff = monthIndex(month) + delta - monthIndex(Calendar.getInstance())
        return diff in 0..11
    }

    fun shift(delta: Int) {
        if (!canShift(delta)) return
        month.add(Calendar.MONTH, delta)
        requestLayout()
        invalidate()
        contentDescription = title()
    }

    fun title(): String {
        val s = SimpleDateFormat("LLLL yyyy", Locale.ENGLISH).format(month.time)
        return s
    }

    // ---------- Geometrie ----------

    private fun daysInMonth() = month.getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Coloana primei zile: luni = 0 ... duminică = 6. */
    private fun firstColumn() = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun rows() = ceil((firstColumn() + daysInMonth()) / 7.0).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        cellW = w / 7f
        rowH = min(cellW, 52 * dp)
        val h = (headerH + rows() * rowH).toInt()
        setMeasuredDimension(w, h)
    }

    private fun dayCal(day: Int): Calendar = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, day)
    }

    // ---------- Desen ----------

    override fun onDraw(canvas: Canvas) {
        val labelY = headerH / 2 - (labelPaint.descent() + labelPaint.ascent()) / 2
        Prefs.DAY_LABELS.forEachIndexed { i, label ->
            canvas.drawText(label, cellW * i + cellW / 2, labelY, labelPaint)
        }

        val todayKey = Prefs.dateKey(Calendar.getInstance())
        val radius = min(cellW, rowH) * 0.38f
        val start = firstColumn()

        for (day in 1..daysInMonth()) {
            val pos = start + day - 1
            val cx = cellW * (pos % 7) + cellW / 2
            val cy = headerH + rowH * (pos / 7) + rowH / 2
            val cal = dayCal(day)
            val key = Prefs.dateKey(cal)
            val past = key < todayKey
            val active = prefs.isActiveOn(cal)
            val alpha = if (past) 90 else 255

            if (active) {
                discPaint.color = heat
                discPaint.alpha = alpha
                canvas.drawCircle(cx, cy, radius, discPaint)
            } else if (!past && key in eventDays) {
                canvas.drawCircle(cx, cy, radius - 1.1f * dp, eventPaint)
            }
            if (key == todayKey) {
                canvas.drawCircle(cx, cy, radius + 3 * dp, ringPaint)
            }

            numPaint.color = if (active) onHeat else ink
            numPaint.alpha = alpha
            numPaint.typeface = Typeface.create(
                "sans-serif-condensed", if (active) Typeface.BOLD else Typeface.NORMAL,
            )
            val textY = cy - (numPaint.descent() + numPaint.ascent()) / 2
            canvas.drawText(day.toString(), cx, textY, numPaint)

            if (!past && prefs.isException(cal)) {
                discPaint.color = if (active) onHeat else heat
                discPaint.alpha = 255
                canvas.drawCircle(cx, cy + radius * 0.68f, 2.4f * dp, discPaint)
            }
        }
    }

    // ---------- Atingere ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (event.y < headerH) return true
                val col = (event.x / cellW).toInt().coerceIn(0, 6)
                val row = ((event.y - headerH) / rowH).toInt()
                val day = row * 7 + col - firstColumn() + 1
                if (day !in 1..daysInMonth()) return true
                val cal = dayCal(day)
                if (Prefs.dateKey(cal) < Prefs.dateKey(Calendar.getInstance())) return true
                prefs.toggleDate(cal)
                performClick()
                invalidate()
                onDayToggled?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
