package ro.ocsike.toyotaclima

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout

    // Culori (se schimbă automat zi/noapte din res/values-night)
    private val ink by lazy { getColor(R.color.ink) }
    private val muted by lazy { getColor(R.color.muted) }
    private val line by lazy { getColor(R.color.line) }
    private val heat by lazy { getColor(R.color.heat) }
    private val cool by lazy { getColor(R.color.cool) }
    private val heroBg by lazy { getColor(R.color.hero_bg) }
    private val heroText by lazy { getColor(R.color.hero_text) }
    private val onHeat by lazy { getColor(R.color.on_heat) }

    private val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val dp: Float by lazy { resources.displayMetrics.density }
    private fun px(v: Number) = (v.toFloat() * dp).toInt()

    // Panoul de sus
    private lateinit var heroSwitch: Switch
    private lateinit var heroTime: TextView
    private lateinit var heroDate: TextView
    private lateinit var heroRule: TextView
    private lateinit var heroLast: TextView

    // Calendar și program
    private lateinit var calendar: MonthCalendarView
    private lateinit var calTitle: TextView
    private lateinit var calPrev: TextView
    private lateinit var calNext: TextView
    private val weekdayViews = mutableListOf<TextView>()
    private lateinit var timesRow: LinearLayout
    private lateinit var notice: LinearLayout

    // Câmpuri
    private lateinit var etHeatBelow: EditText
    private lateinit var etHeatTarget: EditText
    private lateinit var etDefrost: EditText
    private lateinit var etCoolAbove: EditText
    private lateinit var etCoolTarget: EditText
    private lateinit var etDuration: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etVin: EditText
    private lateinit var etLat: EditText
    private lateinit var etLon: EditText
    private lateinit var tvLog: TextView

    private val actionButtons = mutableListOf<TextView>()
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        loadValues()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.schedule(this)
        refreshAll()
    }

    // =====================================================================
    // Construcția ecranului
    // =====================================================================

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(40))
        }
        scroll.addView(root)
        setContentView(scroll)

        buildHero()
        buildNotice()
        buildCalendar()
        buildSchedule()
        buildTemperatures()
        buildActions()
        buildAccount()
        buildLog()
    }

    private fun buildHero() {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(20))
            background = rounded(heroBg, 22)
        }

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("Următoarea pornire", 14, heroText).apply { alpha = 0.75f }, weightParams())
        heroSwitch = Switch(this).apply {
            thumbTintList = checkedTint(heat, 0xFFBFC8D4.toInt())
            trackTintList = checkedTint(heat, 0xFF5B6B80.toInt())
            contentDescription = "Programare activă"
            setOnCheckedChangeListener { _, checked ->
                if (loading) return@setOnCheckedChangeListener
                prefs.enabled = checked
                reschedule()
            }
        }
        top.addView(heroSwitch)
        hero.addView(top)

        heroTime = text("", 64, heat).apply {
            typeface = condensed
            includeFontPadding = false
            setPadding(0, px(6), 0, 0)
        }
        hero.addView(heroTime)
        heroDate = text("", 18, heroText).apply { typeface = medium }
        hero.addView(heroDate)
        heroRule = text("", 14, heroText).apply {
            alpha = 0.75f
            setPadding(0, px(10), 0, 0)
            setLineSpacing(0f, 1.15f)
        }
        hero.addView(heroRule)
        heroLast = text("", 13, heroText).apply {
            alpha = 0.6f
            setPadding(0, px(10), 0, 0)
        }
        hero.addView(heroLast)

        root.addView(hero)
    }

    /** Avertismente de permisiuni: apar doar când lipsește ceva. */
    private fun buildNotice() {
        notice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(12))
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setStroke(px(1.5f), heat)
            }
        }
        root.addView(notice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })
    }

    private fun buildCalendar() {
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(28), 0, px(8))
        }
        calTitle = text("", 22, ink).apply { typeface = medium }
        header.addView(calTitle, weightParams())
        calPrev = roundButton("‹") { calendar.shift(-1); refreshCalendarHeader() }
        calNext = roundButton("›") { calendar.shift(1); refreshCalendarHeader() }
        header.addView(calPrev)
        header.addView(calNext, LinearLayout.LayoutParams(px(40), px(40)).apply { leftMargin = px(8) })
        root.addView(header)

        calendar = MonthCalendarView(this, prefs)
        calendar.onDayToggled = { reschedule() }
        root.addView(calendar)

        root.addView(
            text(
                "Zilele colorate pornesc clima. Atinge o zi ca s-o pornești sau s-o oprești doar pe ea; " +
                    "punctul arată că ai schimbat-o față de programul săptămânal.",
                13, muted,
            ).apply {
                setPadding(0, px(8), 0, 0)
                setLineSpacing(0f, 1.15f)
            },
        )
    }

    private fun buildSchedule() {
        sectionTitle("Program")
        root.addView(text("În fiecare săptămână", 14, muted).apply { setPadding(0, 0, 0, px(8)) })

        val row = LinearLayout(this)
        Prefs.DAY_ORDER.forEachIndexed { i, day ->
            val cell = FrameLayout(this)
            val v = text(Prefs.DAY_LABELS[i], 14, ink).apply {
                gravity = Gravity.CENTER
                typeface = medium
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val days = prefs.days.toMutableSet()
                    if (day in days) days.remove(day) else days.add(day)
                    prefs.days = days
                    refreshWeekdays()
                    reschedule()
                }
            }
            weekdayViews.add(v)
            cell.addView(v, FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER))
            row.addView(cell, weightParams())
        }
        root.addView(row)

        root.addView(text("Ore de pornire", 14, muted).apply { setPadding(0, px(18), 0, px(8)) })
        timesRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(timesRow)
        }
        root.addView(hs)
        root.addView(
            text("Atinge o oră ca s-o schimbi, ține apăsat ca s-o ștergi.", 13, muted)
                .apply { setPadding(0, px(8), 0, 0) },
        )
    }

    private fun buildTemperatures() {
        sectionTitle("Temperaturi")

        group("Încălzire", heat)
        etHeatBelow = numberRow("Pornește când afară e sub", "°C")
        etHeatTarget = numberRow("Temperatura în mașină", "°C")
        etDefrost = numberRow("Dezaburire parbriz sub", "°C")

        group("Răcire", cool)
        etCoolAbove = numberRow("Pornește când afară e peste", "°C")
        etCoolTarget = numberRow("Temperatura în mașină", "°C")

        group("Durată", muted)
        etDuration = numberRow("Cât timp merge climatizarea", "min", decimal = false)
    }

    private fun buildActions() {
        sectionTitle("Acum")
        val row = LinearLayout(this)
        val test = pill("Testează", filled = false) { runAction(ClimaRunner.Mode.TEST) }
        val start = pill("Pornește clima", filled = true) { runAction(ClimaRunner.Mode.START_NOW) }
        val stop = pill("Oprește", filled = false) { runAction(ClimaRunner.Mode.STOP) }
        row.addView(test)
        row.addView(start, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(8) })
        row.addView(stop, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(8) })
        actionButtons += listOf(test, start, stop)
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        root.addView(
            text("Testează verifică vremea și contul Toyota, fără să pornească mașina.", 13, muted)
                .apply { setPadding(0, px(8), 0, 0) },
        )
    }

    private fun buildAccount() {
        val body = collapsible("Cont MyToyota și locație", expanded = prefs.username.isBlank())
        etUser = field(body, "Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPass = field(body, "Parolă", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        etVin = field(body, "VIN (gol = prima mașină din cont)", InputType.TYPE_CLASS_TEXT)
        val num = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        etLat = field(body, "Latitudine pentru vreme", num)
        etLon = field(body, "Longitudine pentru vreme", num)
    }

    private fun buildLog() {
        val body = collapsible("Jurnal", expanded = false)
        tvLog = text("", 12, ink).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.2f)
        }
        body.addView(tvLog)
        body.addView(
            pill("Golește jurnalul", filled = false) { prefs.clearLog(); refreshAll() },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(12) },
        )
    }

    // =====================================================================
    // Componente
    // =====================================================================

    private fun text(s: String, sizeSp: Int, color: Int) = TextView(this).apply {
        text = s
        textSize = sizeSp.toFloat()
        setTextColor(color)
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * dp
    }

    private fun checkedTint(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(on, off),
    )

    private fun ripple(content: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(0x33808080), content, null)

    private fun sectionTitle(s: String) {
        root.addView(text(s, 22, ink).apply {
            typeface = medium
            setPadding(0, px(32), 0, px(10))
        })
    }

    /** Subtitlu de grup cu o bară colorată: chihlimbar = căldură, albastru = răcire. */
    private fun group(s: String, color: Int) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(14), 0, px(2))
        }
        row.addView(View(this).apply { background = rounded(color, 2) }, LinearLayout.LayoutParams(px(4), px(18)))
        row.addView(text(s, 16, ink).apply {
            typeface = medium
            setPadding(px(10), 0, 0, 0)
        })
        root.addView(row)
    }

    private fun numberRow(label: String, unit: String, decimal: Boolean = true): EditText {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(14), 0, 0, 0)
        }
        row.addView(text(label, 15, ink), weightParams())
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            isSingleLine = true
            gravity = Gravity.END
            textSize = 18f
            typeface = condensed
            setTextColor(ink)
            backgroundTintList = ColorStateList.valueOf(line)
            contentDescription = label
            addTextChangedListener(watcher())
        }
        row.addView(et, LinearLayout.LayoutParams(px(64), -2))
        row.addView(text(unit, 15, muted).apply { setPadding(px(4), 0, 0, 0) }, LinearLayout.LayoutParams(px(36), -2))
        root.addView(row)
        return et
    }

    private fun field(parent: LinearLayout, hint: String, type: Int): EditText {
        parent.addView(text(hint, 13, muted).apply { setPadding(0, px(10), 0, 0) })
        val et = EditText(this).apply {
            inputType = type
            isSingleLine = true
            textSize = 16f
            setTextColor(ink)
            backgroundTintList = ColorStateList.valueOf(line)
            contentDescription = hint
            addTextChangedListener(watcher())
        }
        parent.addView(et)
        return et
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        typeface = medium
        gravity = Gravity.CENTER
        setPadding(px(18), px(11), px(18), px(11))
        setTextColor(if (filled) onHeat else ink)
        background = ripple(GradientDrawable().apply {
            cornerRadius = 999 * dp
            if (filled) setColor(heat) else setStroke(px(1.5f), line)
        })
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun roundButton(label: String, onClick: () -> Unit) = text(label, 22, ink).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(px(40), px(40))
        background = ripple(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(px(1.5f), line)
        })
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Secțiune pliabilă; întoarce containerul în care se pun câmpurile. */
    private fun collapsible(title: String, expanded: Boolean): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(32), 0, px(6))
            isClickable = true
            isFocusable = true
        }
        val chevron = text(if (expanded) "▾" else "▸", 18, muted)
        header.addView(text(title, 22, ink).apply { typeface = medium }, weightParams())
        header.addView(chevron)
        header.setOnClickListener {
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            chevron.text = if (show) "▾" else "▸"
        }
        root.addView(header)
        root.addView(body)
        return body
    }

    private fun watcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = saveFields()
    }

    // =====================================================================
    // Date
    // =====================================================================

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun EditText.dbl(def: Double) =
        text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def

    private fun loadValues() {
        loading = true
        heroSwitch.isChecked = prefs.enabled
        etHeatBelow.setText(fmt(prefs.heatBelow))
        etHeatTarget.setText(fmt(prefs.heatTarget))
        etDefrost.setText(fmt(prefs.defrostBelow))
        etCoolAbove.setText(fmt(prefs.coolAbove))
        etCoolTarget.setText(fmt(prefs.coolTarget))
        etDuration.setText(prefs.duration.toString())
        etUser.setText(prefs.username)
        etPass.setText(prefs.password)
        etVin.setText(prefs.vin)
        etLat.setText(prefs.latitude.toString())
        etLon.setText(prefs.longitude.toString())
        loading = false
    }

    /** Salvare automată la fiecare modificare a câmpurilor. */
    private fun saveFields() {
        if (loading) return
        val newUser = etUser.text.toString().trim()
        val newPass = etPass.text.toString()
        if (newUser != prefs.username || newPass != prefs.password) {
            ToyotaApi(prefs).clearTokens()
        }
        prefs.username = newUser
        prefs.password = newPass
        prefs.vin = etVin.text.toString().trim()
        prefs.heatBelow = etHeatBelow.dbl(prefs.heatBelow)
        prefs.heatTarget = etHeatTarget.dbl(prefs.heatTarget)
        prefs.defrostBelow = etDefrost.dbl(prefs.defrostBelow)
        prefs.coolAbove = etCoolAbove.dbl(prefs.coolAbove)
        prefs.coolTarget = etCoolTarget.dbl(prefs.coolTarget)
        prefs.duration = etDuration.text.toString().trim().toIntOrNull()?.coerceIn(1, 30) ?: prefs.duration
        prefs.latitude = etLat.dbl(prefs.latitude)
        prefs.longitude = etLon.dbl(prefs.longitude)
        refreshHero()
    }

    private fun reschedule() {
        Scheduler.schedule(this)
        refreshHero()
        calendar.invalidate()
    }

    private fun setTimes(list: List<Pair<Int, Int>>) {
        prefs.times = list.distinct().sortedBy { it.first * 60 + it.second }
            .joinToString(", ") { (h, m) -> String.format(Locale.US, "%02d:%02d", h, m) }
        refreshTimes()
        reschedule()
    }

    // =====================================================================
    // Afișare
    // =====================================================================

    private fun refreshAll() {
        refreshHero()
        refreshNotice()
        refreshCalendarHeader()
        calendar.invalidate()
        refreshWeekdays()
        refreshTimes()
        tvLog.text = prefs.logText().ifBlank { "Nimic încă. Fiecare pornire și fiecare test apar aici." }
    }

    private fun refreshHero() {
        val next = prefs.nextRun
        val now = System.currentTimeMillis()
        when {
            !prefs.enabled -> {
                heroTime.text = "Oprit"
                heroDate.text = "Pornește comutatorul ca să programezi clima."
            }
            next > now -> {
                heroTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next))
                heroDate.text = dayName(next)
            }
            prefs.parsedTimes().isEmpty() -> {
                heroTime.text = "—"
                heroDate.text = "Adaugă cel puțin o oră de pornire."
            }
            else -> {
                heroTime.text = "—"
                heroDate.text = "Nicio zi programată în următoarele două luni."
            }
        }
        heroRule.text = "Încălzește la ${fmt(prefs.heatTarget)}° când afară e sub ${fmt(prefs.heatBelow)}°, " +
            "răcește la ${fmt(prefs.coolTarget)}° peste ${fmt(prefs.coolAbove)}°. Între ele nu pornește."
        val last = prefs.logText().lineSequence().firstOrNull { it.isNotBlank() }
        heroLast.text = if (last != null) "Ultima: $last" else ""
        heroLast.visibility = if (last != null) View.VISIBLE else View.GONE
    }

    private fun dayName(t: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = t }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val full = SimpleDateFormat("EEEE, d MMMM", Locale("ro")).format(Date(t))
        return when (Prefs.dateKey(target)) {
            Prefs.dateKey(today) -> "Azi"
            Prefs.dateKey(tomorrow) -> "Mâine, ${full.substringBefore(',')}"
            else -> full.replaceFirstChar { it.titlecase(Locale("ro")) }
        }
    }

    private fun refreshCalendarHeader() {
        calTitle.text = calendar.title()
        calPrev.alpha = if (calendar.canShift(-1)) 1f else 0.3f
        calNext.alpha = if (calendar.canShift(1)) 1f else 0.3f
    }

    private fun refreshWeekdays() {
        val days = prefs.days
        Prefs.DAY_ORDER.forEachIndexed { i, day ->
            val on = day in days
            val v = weekdayViews[i]
            v.setTextColor(if (on) onHeat else ink)
            v.background = ripple(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (on) setColor(heat) else setStroke(px(1.5f), line)
            })
            v.contentDescription = Prefs.DAY_LABELS[i] + if (on) ", pornit" else ", oprit"
        }
    }

    private fun refreshTimes() {
        timesRow.removeAllViews()
        val times = prefs.parsedTimes()
        times.forEachIndexed { idx, (h, m) ->
            val label = String.format(Locale.US, "%02d:%02d", h, m)
            val chip = pill(label, filled = false) {
                TimePickerDialog(this, { _, nh, nm ->
                    val list = times.toMutableList()
                    list[idx] = nh to nm
                    setTimes(list)
                }, h, m, true).show()
            }.apply {
                textSize = 18f
                typeface = condensed
                setOnLongClickListener {
                    setTimes(times.filterIndexed { i, _ -> i != idx })
                    Toast.makeText(this@MainActivity, "Ora $label a fost ștearsă", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            timesRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = px(8) })
        }
        val add = pill("+ Adaugă oră", filled = false) {
            TimePickerDialog(this, { _, nh, nm -> setTimes(times + (nh to nm)) }, 7, 0, true).show()
        }.apply { setTextColor(muted) }
        timesRow.addView(add)
    }

    private fun refreshNotice() {
        notice.removeAllViews()
        val exact = Scheduler.canScheduleExact(this)
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (!exact) {
            noticeRow("Android poate întârzia pornirea fără permisiunea pentru alarme exacte.", "Permite") {
                openExactAlarmSettings()
            }
        }
        if (!battery) {
            noticeRow("Optimizarea bateriei poate opri pornirea de dimineață.", "Dezactivează") {
                openBatterySettings()
            }
        }
        notice.visibility = if (notice.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun noticeRow(message: String, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(4), 0, px(4))
        }
        row.addView(text(message, 14, ink).apply { setPadding(0, 0, px(10), 0) }, weightParams())
        row.addView(pill(action, filled = true, onClick = onClick).apply {
            textSize = 14f
            setPadding(px(14), px(8), px(14), px(8))
        })
        notice.addView(row)
    }

    // =====================================================================
    // Acțiuni
    // =====================================================================

    private fun runAction(mode: ClimaRunner.Mode) {
        actionButtons.forEach { it.isEnabled = false; it.alpha = 0.4f }
        heroLast.visibility = View.VISIBLE
        heroLast.text = when (mode) {
            ClimaRunner.Mode.TEST -> "Verific vremea și contul…"
            ClimaRunner.Mode.STOP -> "Opresc clima…"
            else -> "Pornesc clima…"
        }
        Thread {
            val msg = ClimaRunner.run(applicationContext, mode)
            runOnUiThread {
                actionButtons.forEach { it.isEnabled = true; it.alpha = 1f }
                refreshAll()
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                return
            } catch (e: Exception) { /* cade pe setările aplicației */ }
        }
        openAppSettings()
    }

    private fun openBatterySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
