package com.feuch.clochette

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

class SensorObserver(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var lastAcceleration = 0f
    private var movementScore = 0f
    private var lowLight = false
    private var orientation = "unknown"
    private var screenActive = true
    private var started = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenActive = intent?.action != Intent.ACTION_SCREEN_OFF
        }
    }

    fun start() {
        if (started) return
        started = true
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        context.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager.unregisterListener(this)
        runCatching { context.unregisterReceiver(screenReceiver) }
    }

    fun snapshot(): SensorSnapshot = SensorSnapshot(
        walkingPossible = movementScore > 1.8f,
        phoneStill = movementScore < 0.35f,
        lowLight = lowLight,
        orientation = orientation,
        screenActive = screenActive,
    )

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2],
                )
                val delta = abs(magnitude - lastAcceleration)
                movementScore = (movementScore * 0.85f) + (delta * 0.15f)
                lastAcceleration = magnitude
                orientation = if (abs(event.values[0]) > abs(event.values[1])) "landscape-ish" else "portrait-ish"
            }
            Sensor.TYPE_LIGHT -> lowLight = event.values.firstOrNull()?.let { it < 12f } ?: false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
