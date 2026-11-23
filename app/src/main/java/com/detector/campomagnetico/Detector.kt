package com.detector.campomagnetico

import kotlin.math.abs
import kotlin.math.sqrt

enum class Level { IDLE, OK, WEAK, STRONG }

/**
 * Lógica de detección, sin nada de Android para que sea fácil de probar.
 *
 * Se usa la magnitud total del campo (sqrt(x² + y² + z²)). La magnitud no cambia
 * cuando giras el celular, así que solo se mueve cuando algo altera el campo.
 * Primero se mide una base durante [CAL_MS] milisegundos. Después se mira cuánto
 * se aleja la lectura de esa base.
 */
class Detector(var threshold: Float) {

    companion object {
        const val CAL_MS = 3000L
        private const val MIN_CAL_SAMPLES = 10
    }

    var calibrating = false
        private set

    var calProgress = 0f
        private set

    var baseline = 0f
        private set

    /** Desviación suavizada respecto a la base, en µT. */
    var smoothed = 0f
        private set

    var peak = 0f
        private set

    var level = Level.IDLE
        private set

    /** Verdadero si el campo se movió demasiado durante la calibración. */
    var calibrationNoisy = false
        private set

    val alerting: Boolean
        get() = level == Level.WEAK || level == Level.STRONG

    private val calSamples = ArrayList<Float>()
    private var calStartNs = -1L

    fun startCalibration() {
        calibrating = true
        calProgress = 0f
        calSamples.clear()
        calStartNs = -1L
        baseline = 0f
        smoothed = 0f
        peak = 0f
        calibrationNoisy = false
        level = Level.IDLE
    }

    /** Procesa una muestra. [timestampNs] es el tiempo del evento del sensor. Devuelve la magnitud total. */
    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long): Float {
        val m = sqrt(x * x + y * y + z * z)

        if (calibrating) {
            if (calStartNs < 0) calStartNs = timestampNs
            calSamples.add(m)
            val elapsedMs = (timestampNs - calStartNs) / 1_000_000L
            calProgress = (elapsedMs.toFloat() / CAL_MS).coerceIn(0f, 1f)
            if (elapsedMs >= CAL_MS && calSamples.size >= MIN_CAL_SAMPLES) finishCalibration()
            return m
        }

        val d = abs(m - baseline)
        smoothed = smoothed * 0.7f + d * 0.3f
        if (smoothed > peak) peak = smoothed

        val t = threshold
        level = when {
            smoothed >= t * 3f -> Level.STRONG
            level == Level.STRONG && smoothed >= t * 2.4f -> Level.STRONG
            smoothed >= t -> Level.WEAK
            level == Level.WEAK && smoothed >= t * 0.8f -> Level.WEAK
            else -> Level.OK
        }

        // La base sigue los cambios lentos del ambiente solo cuando no hay alerta.
        if (level == Level.OK) baseline += (m - baseline) * 0.002f

        return m
    }

    private fun finishCalibration() {
        val n = calSamples.size
        var sum = 0.0
        for (v in calSamples) sum += v
        val mean = sum / n
        var sq = 0.0
        for (v in calSamples) {
            val diff = v - mean
            sq += diff * diff
        }
        val sigma = sqrt(sq / n).toFloat()

        baseline = mean.toFloat()
        calibrationNoisy = sigma * 3f > threshold
        calibrating = false
        smoothed = 0f
        peak = 0f
        level = Level.OK
    }
}
