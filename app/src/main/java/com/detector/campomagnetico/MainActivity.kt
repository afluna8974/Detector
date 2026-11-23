package com.detector.campomagnetico

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Intervalo entre actualizaciones de pantalla: 50 ms (unas 20 por segundo). */
private const val UI_INTERVAL_NS = 50_000_000L

/** Tiempo mínimo entre vibraciones: 400 ms. */
private const val VIB_INTERVAL_NS = 400_000_000L

/** Periodo de muestreo del sensor en microsegundos (50 lecturas por segundo). */
private const val SAMPLING_US = 20_000

private const val NOTES =
    "CÓMO USARLO\n" +
        "• Calibra lejos de otros aparatos, con el celular quieto. Dura unos 3 segundos.\n" +
        "• Acerca el celular despacio a lo que quieres revisar. Cuanto más cerca, más sube la desviación.\n" +
        "• Si la desviación sube sola, hay metal o imanes cerca de ti. Recalibra en otro lugar.\n\n" +
        "QUÉ PUEDE Y QUÉ NO PUEDE HACER\n" +
        "• Detecta imanes y campos de bocinas, motores, cargadores y baterías a pocos centímetros.\n" +
        "• No capta señales de radio como Wi-Fi, Bluetooth o llamadas. Ningún celular normal lo hace.\n" +
        "• No distingue un teléfono de otro objeto metálico o imantado. Solo avisa que algo altera el campo."

class MainActivity : Activity(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null
    private val detector = Detector(4f)

    private var running = false
    private var wasRunning = false
    private var prevCalibrating = false
    private var accuracyBanner = false
    private var lastUiNs = 0L
    private var lastVibNs = 0L

    private var tone: ToneGenerator? = null
    private var beeping = false

    private val vibrator: Vibrator? by lazy { findVibrator() }

    private lateinit var banner: TextView
    private lateinit var devText: TextView
    private lateinit var statusText: TextView
    private lateinit var scaleText: TextView
    private lateinit var meter: MeterView
    private lateinit var chart: ChartView
    private lateinit var magText: TextView
    private lateinit var baseText: TextView
    private lateinit var peakText: TextView
    private lateinit var xText: TextView
    private lateinit var yText: TextView
    private lateinit var zText: TextView
    private lateinit var startBtn: Button
    private lateinit var recalBtn: Button
    private lateinit var thrText: TextView
    private lateinit var soundCheck: CheckBox
    private lateinit var vibCheck: CheckBox

    private val soundOn: Boolean
        get() = soundCheck.isChecked

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: RuntimeException) {
            null
        }

        setContentView(buildUi())

        if (magnetometer == null) {
            showBanner("Este celular no tiene magnetómetro, así que esta app no puede medir el campo.", true)
            setBtn(startBtn, false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (running) {
            wasRunning = true
            stopRunning()
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasRunning) {
            wasRunning = false
            startRunning()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
    }

    // ---------------------------------------------------------------- control

    private fun toggle() {
        if (running) stopRunning() else startRunning()
    }

    private fun startRunning() {
        val sensor = magnetometer ?: return
        clearBanner()
        val ok = sensorManager.registerListener(this, sensor, SAMPLING_US)
        if (!ok) {
            showBanner("No se pudo activar el sensor.", true)
            return
        }
        running = true
        prevCalibrating = true
        detector.startCalibration()
        chart.clear()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startBtn.text = "Detener"
        setBtn(recalBtn, true)
        meter.set(0f, Pal.MUTED)
        setStatus("Calibrando… mantén el celular quieto", Pal.MUTED)
    }

    private fun stopRunning() {
        sensorManager.unregisterListener(this)
        running = false
        beeping = false
        handler.removeCallbacks(beepRunnable)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startBtn.text = "Iniciar y calibrar"
        setBtn(recalBtn, false)
        devText.text = "0.0"
        meter.set(0f, Pal.OK)
        setStatus("Esperando inicio", Pal.MUTED)
    }

    private fun recalibrate() {
        clearBanner()
        prevCalibrating = true
        detector.startCalibration()
        chart.clear()
        meter.set(0f, Pal.MUTED)
        setStatus("Calibrando… mantén el celular quieto", Pal.MUTED)
    }

    private fun setThreshold(t: Float) {
        detector.threshold = t
        chart.threshold = t
        thrText.text = "${f1(t)} µT"
        scaleText.text = "${(t * 6f).toInt()} µT"
    }

    // ---------------------------------------------------------------- sensor

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !running || event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val m = detector.onSample(x, y, z, event.timestamp)
        if (event.timestamp - lastUiNs < UI_INTERVAL_NS) return
        lastUiNs = event.timestamp
        updateUi(m, x, y, z, event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!running) return
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            accuracyBanner = true
            showBanner("El sensor pide calibración. Mueve el celular dibujando un ocho en el aire.", false)
        } else if (accuracyBanner) {
            accuracyBanner = false
            clearBanner()
        }
    }

    private fun updateUi(m: Float, x: Float, y: Float, z: Float, nowNs: Long) {
        magText.text = "${f1(m)} µT"
        xText.text = f1(x)
        yText.text = f1(y)
        zText.text = f1(z)

        if (detector.calibrating) {
            meter.set(detector.calProgress, Pal.MUTED)
            return
        }

        if (prevCalibrating) {
            prevCalibrating = false
            if (detector.calibrationNoisy) {
                showBanner(
                    "El campo cambió mucho al calibrar. Hay metal o un imán cerca, o el celular se movió. Recalibra en otro lugar.",
                    false
                )
            } else if (!accuracyBanner) {
                clearBanner()
            }
        }

        val s = detector.smoothed
        val color: Int
        val label: String
        when (detector.level) {
            Level.STRONG -> {
                color = Pal.STRONG
                label = "Fuente magnética muy cerca"
            }
            Level.WEAK -> {
                color = Pal.WEAK
                label = "Señal débil"
            }
            else -> {
                color = Pal.OK
                label = "Sin señal"
            }
        }

        devText.text = f1(s)
        baseText.text = "${f1(detector.baseline)} µT"
        peakText.text = "${f1(detector.peak)} µT"
        setStatus(label, color)
        meter.set(s / (detector.threshold * 6f), color)
        chart.push(s)

        if (detector.level == Level.STRONG) vibrateBrief(nowNs)
        maybeBeep()
    }

    // ---------------------------------------------------------------- alertas

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!running || !soundOn || !detector.alerting) {
                beeping = false
                return
            }
            try {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            } catch (e: RuntimeException) {
                // Sin sonido en este momento; se intenta de nuevo en el siguiente pitido.
            }
            val interval = (700f - detector.smoothed * 25f).toLong().coerceIn(90L, 700L)
            handler.postDelayed(this, interval)
        }
    }

    private fun maybeBeep() {
        if (soundOn && detector.alerting && !beeping) {
            beeping = true
            handler.post(beepRunnable)
        }
    }

    private fun vibrateBrief(nowNs: Long) {
        if (!vibCheck.isChecked || nowNs - lastVibNs < VIB_INTERVAL_NS) return
        lastVibNs = nowNs
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Suppress("DEPRECATION")
    private fun findVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

    // ---------------------------------------------------------------- interfaz

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            isFillViewport = true
            fitsSystemWindows = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))

        col.addView(tv("Detector de campo magnético", 20f, Pal.FG, bold = true))
        col.addView(
            tv("Usa el magnetómetro del celular para avisar cuando hay un aparato electrónico muy cerca.", 13f, Pal.MUTED),
            lp(top = 2)
        )

        banner = tv("", 13f, Pal.WEAK).apply {
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        col.addView(banner, lp(top = 16))

        // Panel 1: lectura principal
        val p1 = panel()
        p1.addView(tv("DESVIACIÓN DEL CAMPO", 11f, Pal.MUTED).apply { letterSpacing = 0.1f })
        val readRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        devText = tv("0.0", 52f, Pal.FG, bold = true, mono = true)
        readRow.addView(devText)
        readRow.addView(
            tv(" µT", 16f, Pal.MUTED, mono = true),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(8) }
        )
        p1.addView(readRow, lp(top = 4))
        statusText = tv("Esperando inicio", 16f, Pal.MUTED, bold = true)
        p1.addView(statusText, lp(top = 8))
        meter = MeterView(this)
        p1.addView(meter, LinearLayout.LayoutParams(MATCH, dp(14)).apply { topMargin = dp(14) })
        val scaleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scaleRow.addView(tv("0", 11f, Pal.MUTED, mono = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        scaleText = tv("24 µT", 11f, Pal.MUTED, mono = true)
        scaleText.gravity = Gravity.END
        scaleRow.addView(scaleText, LinearLayout.LayoutParams(0, WRAP, 1f))
        p1.addView(scaleRow, lp(top = 4))
        chart = ChartView(this)
        p1.addView(chart, LinearLayout.LayoutParams(MATCH, dp(130)).apply { topMargin = dp(10) })
        col.addView(p1, lp(top = 16))

        // Panel 2: lectura cruda
        val p2 = panel()
        p2.addView(tv("LECTURA CRUDA", 11f, Pal.MUTED).apply { letterSpacing = 0.1f })
        val (row1, vals1) = statRow(listOf("Total", "Base", "Pico"))
        magText = vals1[0]
        baseText = vals1[1]
        peakText = vals1[2]
        p2.addView(row1, lp(top = 8))
        val (row2, vals2) = statRow(listOf("X", "Y", "Z"))
        xText = vals2[0]
        yText = vals2[1]
        zText = vals2[2]
        p2.addView(row2, lp(top = 8))
        col.addView(p2, lp(top = 16))

        // Panel 3: controles
        val p3 = panel()
        startBtn = button("Iniciar y calibrar", Pal.OK, Pal.ON_OK).apply {
            setOnClickListener { toggle() }
        }
        p3.addView(startBtn, LinearLayout.LayoutParams(MATCH, dp(48)))
        recalBtn = button("Recalibrar", Pal.BTN, Pal.FG).apply {
            setOnClickListener { if (running) recalibrate() }
        }
        setBtn(recalBtn, false)
        p3.addView(recalBtn, LinearLayout.LayoutParams(MATCH, dp(48)).apply { topMargin = dp(8) })

        val thrRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        thrRow.addView(tv("Umbral de alerta", 14f, Pal.FG), LinearLayout.LayoutParams(0, WRAP, 1f))
        thrText = tv("4.0 µT", 14f, Pal.FG, mono = true)
        thrRow.addView(thrText)
        p3.addView(thrRow, lp(top = 16))

        val seek = SeekBar(this).apply {
            max = 38
            progress = 6
            progressTintList = ColorStateList.valueOf(Pal.OK)
            thumbTintList = ColorStateList.valueOf(Pal.OK)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setThreshold(1f + progress * 0.5f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        p3.addView(seek, lp(top = 4))

        soundCheck = check("Pitidos más rápidos al acercarte", false)
        p3.addView(soundCheck, lp(top = 8))
        vibCheck = check("Vibrar con señal fuerte", true)
        p3.addView(vibCheck, lp(top = 4))
        col.addView(p3, lp(top = 16))

        col.addView(tv(NOTES, 13f, Pal.MUTED), lp(top = 20))

        setThreshold(4f)
        return scroll
    }

    private fun statRow(labels: List<String>): Pair<LinearLayout, List<TextView>> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val values = ArrayList<TextView>()
        labels.forEachIndexed { i, label ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Pal.CELL, Pal.LINE, 6)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            cell.addView(tv(label.uppercase(Locale.ROOT), 11f, Pal.MUTED))
            val v = tv("–", 16f, Pal.FG, bold = true, mono = true)
            cell.addView(v)
            values.add(v)
            row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
        return Pair(row, values)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Pal.PANEL, Pal.LINE, 10)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun tv(s: String, sp: Float, color: Int, bold: Boolean = false, mono: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            typeface = if (mono) Typeface.create(Typeface.MONOSPACE, style) else Typeface.create(Typeface.DEFAULT, style)
        }

    private fun button(s: String, bg: Int, fg: Int): Button = Button(this).apply {
        text = s
        isAllCaps = false
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(bg, Pal.LINE, 8)
        stateListAnimator = null
    }

    private fun check(s: String, on: Boolean): CheckBox = CheckBox(this).apply {
        text = s
        isChecked = on
        setTextColor(Pal.FG)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        buttonTintList = ColorStateList.valueOf(Pal.OK)
    }

    private fun setBtn(b: Button, enabled: Boolean) {
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.45f
    }

    private fun setStatus(text: String, color: Int) {
        statusText.text = text
        statusText.setTextColor(color)
    }

    private fun showBanner(msg: String, error: Boolean) {
        val color = if (error) Pal.STRONG else Pal.WEAK
        banner.text = msg
        banner.setTextColor(color)
        banner.background = rounded(Color.TRANSPARENT, color, 8)
        banner.visibility = View.VISIBLE
    }

    private fun clearBanner() {
        banner.visibility = View.GONE
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp)
        setStroke(dp(1), stroke)
    }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dpf(v: Int): Float = v * resources.displayMetrics.density

    private fun f1(v: Float): String = String.format(Locale.US, "%.1f", v)
}
