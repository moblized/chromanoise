package app.chromanoise.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Watches the motion sensors while an alarm rings and calls [onFlip] when the phone is turned
 * face down. Uses the gravity sensor if there is one, otherwise a smoothed accelerometer.
 * The decision itself is made by [FlipDetector].
 */
class FlipToSnooze(context: Context, private val onFlip: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val detector = FlipDetector()
    private val handler = Handler(Looper.getMainLooper())
    private var smoothing = false
    private var smoothedZ = Float.NaN
    private var active = false

    fun start() {
        val sm = sensors ?: return
        val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravity ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        smoothing = gravity == null
        active = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, handler)
    }

    fun stop() {
        if (!active) return
        active = false
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        var z = event.values[2]
        if (smoothing) {
            // Low-pass the raw accelerometer so hand shake doesn't look like gravity.
            smoothedZ = if (smoothedZ.isNaN()) z else smoothedZ * 0.8f + z * 0.2f
            z = smoothedZ
        }
        if (detector.onGravityZ(z, SystemClock.elapsedRealtime())) {
            stop()
            onFlip()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
