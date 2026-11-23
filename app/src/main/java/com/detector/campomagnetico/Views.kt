package com.detector.campomagnetico

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Barra horizontal con marcas en el umbral débil (1/6) y en el umbral fuerte (1/2). */
class MeterView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private var fraction = 0f
    private var fillColor = Pal.OK

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.CELL
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.LINE
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.FG
        alpha = 90
        strokeWidth = d
    }

    fun set(f: Float, color: Int) {
        fraction = f.coerceIn(0f, 1f)
        fillColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 4f * d
        canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
        if (fraction > 0f) {
            fillPaint.color = fillColor
            canvas.drawRoundRect(0f, 0f, maxOf(w * fraction, 2f * r), h, r, r, fillPaint)
        }
        canvas.drawLine(w / 6f, 0f, w / 6f, h, tickPaint)
        canvas.drawLine(w / 2f, 0f, w / 2f, h, tickPaint)
        canvas.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, borderPaint)
    }
}

/** Registro de las últimas muestras de desviación, con las líneas de umbral. */
class ChartView(context: Context) : View(context) {

    var threshold = 4f
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val cap = 240
    private val data = FloatArray(cap)
    private var count = 0
    private val path = Path()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.LINE
        strokeWidth = d
    }
    private val weakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.WEAK
        strokeWidth = d
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * d, 4f * d), 0f)
    }
    private val strongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.STRONG
        strokeWidth = d
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * d, 4f * d), 0f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.FG
        strokeWidth = 2f * d
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    fun push(v: Float) {
        if (count < cap) {
            data[count] = v
            count++
        } else {
            System.arraycopy(data, 1, data, 0, cap - 1)
            data[cap - 1] = v
        }
        invalidate()
    }

    fun clear() {
        count = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 6f * d
        val span = h - 2f * pad

        var max = threshold * 6f
        for (i in 0 until count) {
            if (data[i] * 1.1f > max) max = data[i] * 1.1f
        }

        for (i in 0..3) {
            val gy = pad + i * span / 3f
            canvas.drawLine(0f, gy, w, gy, gridPaint)
        }

        val weakY = h - pad - (threshold / max) * span
        val strongY = h - pad - (threshold * 3f / max) * span
        canvas.drawLine(0f, weakY, w, weakY, weakPaint)
        canvas.drawLine(0f, strongY, w, strongY, strongPaint)

        if (count < 2) return
        path.reset()
        for (i in 0 until count) {
            val px = i.toFloat() / (cap - 1) * w
            val py = h - pad - (data[i] / max) * span
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, linePaint)
    }
}
