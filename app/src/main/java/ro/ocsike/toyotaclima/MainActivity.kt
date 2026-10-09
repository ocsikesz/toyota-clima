package ro.ocsike.toyotaclima

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout

    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etVin: EditText
    private lateinit var swEnabled: Switch
    private lateinit var etTimes: EditText
    private val dayBoxes = mutableListOf<CheckBox>()
    private lateinit var etHeatBelow: EditText
    private lateinit var etCoolAbove: EditText
    private lateinit var etHeatTarget: EditText
    private lateinit var etCoolTarget: EditText
    private lateinit var etDefrost: EditText
    private lateinit var etDuration: EditText
    private lateinit var etLat: EditText
    private lateinit var etLon: EditText
    private lateinit var tvNext: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private val actionButtons = mutableListOf<Button>()

    private val dp: Float by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        loadValues()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        section("Cont MyToyota")
        etUser = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        etPass = field("Parolă", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        etVin = field("VIN (opțional, gol = prima mașină din cont)", InputType.TYPE_CLASS_TEXT)

        section("Programare")
        swEnabled = Switch(this).apply { text = "Programare activă" }
        root.addView(swEnabled)
        etTimes = field("Ore de pornire (ex: 06:20, 14:30)", InputType.TYPE_CLASS_TEXT)
        root.addView(label("Zile"))
        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (lbl in Prefs.DAY_LABELS) {
            val cb = CheckBox(this).apply { text = lbl }
            dayBoxes.add(cb)
            daysRow.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(daysRow)

        section("Praguri (°C)")
        val num = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        etHeatBelow = field("Încălzire dacă afară e sub", num)
        etHeatTarget = field("Temperatură țintă la încălzire", num)
        etCoolAbove = field("Răcire dacă afară e peste", num)
        etCoolTarget = field("Temperatură țintă la răcire", num)
        etDefrost = field("Dezaburire parbriz/lunetă sub", num)
        etDuration = field("Durată (minute)", InputType.TYPE_CLASS_NUMBER)

        section("Locația pentru vreme")
        etLat = field("Latitudine", num)
        etLon = field("Longitudine", num)

        root.addView(button("Salvează și programează") { save(showToast = true) })

        section("Stare")
        tvNext = TextView(this).apply { textSize = 15f; setTypeface(typeface, Typeface.BOLD) }
        root.addView(tvNext)
        tvStatus = TextView(this).apply { textSize = 13f; setPadding(0, px(4), 0, px(8)) }
        root.addView(tvStatus)
        root.addView(button("Permite alarme exacte") { openExactAlarmSettings() })
        root.addView(button("Scoate din optimizarea bateriei") { openBatterySettings() })

        section("Acțiuni")
        actionButtons += button("Test (vreme + login, fără comandă)") { runAction(ClimaRunner.Mode.TEST) }
        actionButtons += button("Rulează acum ca la ora programată") { runAction(ClimaRunner.Mode.SCHEDULED) }
        actionButtons += button("Pornește climatizarea acum") { runAction(ClimaRunner.Mode.START_NOW) }
        actionButtons += button("Oprește climatizarea") { runAction(ClimaRunner.Mode.STOP) }
        actionButtons.forEach { root.addView(it) }

        section("Jurnal")
        tvLog = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(tvLog)
        root.addView(button("Șterge jurnalul") { prefs.clearLog(); refreshStatus() })
    }

    private fun section(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, px(20), 0, px(6))
        })
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, px(8), 0, 0)
    }

    private fun field(hint: String, type: Int): EditText {
        root.addView(label(hint))
        val et = EditText(this).apply {
            inputType = type
            isSingleLine = true
        }
        root.addView(et)
        return et
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    // ---------- Date ----------

    private fun loadValues() {
        etUser.setText(prefs.username)
        etPass.setText(prefs.password)
        etVin.setText(prefs.vin)
        swEnabled.isChecked = prefs.enabled
        etTimes.setText(prefs.times)
        val days = prefs.days
        Prefs.DAY_ORDER.forEachIndexed { i, d -> dayBoxes[i].isChecked = d in days }
        etHeatBelow.setText(fmt(prefs.heatBelow))
        etHeatTarget.setText(fmt(prefs.heatTarget))
        etCoolAbove.setText(fmt(prefs.coolAbove))
        etCoolTarget.setText(fmt(prefs.coolTarget))
        etDefrost.setText(fmt(prefs.defrostBelow))
        etDuration.setText(prefs.duration.toString())
        etLat.setText(prefs.latitude.toString())
        etLon.setText(prefs.longitude.toString())
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

    private fun EditText.dbl(def: Double) = text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def

    private fun save(showToast: Boolean) {
        val newUser = etUser.text.toString().trim()
        val newPass = etPass.text.toString()
        if (newUser != prefs.username || newPass != prefs.password) {
            ToyotaApi(prefs).clearTokens()
        }
        prefs.username = newUser
        prefs.password = newPass
        prefs.vin = etVin.text.toString().trim()
        prefs.enabled = swEnabled.isChecked
        prefs.times = etTimes.text.toString().trim()
        prefs.days = Prefs.DAY_ORDER.filterIndexed { i, _ -> dayBoxes[i].isChecked }.toSet()
        prefs.heatBelow = etHeatBelow.dbl(prefs.heatBelow)
        prefs.heatTarget = etHeatTarget.dbl(prefs.heatTarget)
        prefs.coolAbove = etCoolAbove.dbl(prefs.coolAbove)
        prefs.coolTarget = etCoolTarget.dbl(prefs.coolTarget)
        prefs.defrostBelow = etDefrost.dbl(prefs.defrostBelow)
        prefs.duration = etDuration.text.toString().trim().toIntOrNull()?.coerceIn(1, 30) ?: prefs.duration
        prefs.latitude = etLat.dbl(prefs.latitude)
        prefs.longitude = etLon.dbl(prefs.longitude)

        val next = Scheduler.schedule(this)
        refreshStatus()
        if (showToast) {
            val msg = when {
                !prefs.enabled -> "Salvat. Programarea e oprită."
                prefs.parsedTimes().isEmpty() -> "Salvat, dar orele nu sunt valide (ex: 06:20, 14:30)."
                next == null -> "Salvat, dar nu e nicio zi bifată."
                else -> "Salvat. Următoarea rulare: ${formatTime(next)}"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun formatTime(t: Long) =
        SimpleDateFormat("EEEE, dd.MM 'la' HH:mm", Locale("ro")).format(Date(t))

    private fun refreshStatus() {
        val next = prefs.nextRun
        tvNext.text = if (prefs.enabled && next > System.currentTimeMillis()) {
            "Următoarea rulare: ${formatTime(next)}"
        } else {
            "Nicio rulare programată"
        }
        val exact = Scheduler.canScheduleExact(this)
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        tvStatus.text = buildString {
            append(if (exact) "✓ Alarme exacte permise" else "✗ Alarme exacte nepermise — ora poate întârzia")
            append('\n')
            append(if (battery) "✓ Fără optimizare baterie" else "✗ Optimizare baterie activă — recomand s-o scoți")
        }
        tvLog.text = prefs.logText().ifBlank { "(gol)" }
    }

    private fun runAction(mode: ClimaRunner.Mode) {
        save(showToast = false)
        actionButtons.forEach { it.isEnabled = false }
        tvNext.text = "Lucrez…"
        Thread {
            val msg = ClimaRunner.run(applicationContext, mode)
            runOnUiThread {
                actionButtons.forEach { it.isEnabled = true }
                refreshStatus()
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
