/*
* Copyright (C) 2022 Stanislav Georgiev
* https://github.com/slaviboy
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.slaviboy.liquidglass.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Rotation (degrees) of the specular light caused by tilting the device. A light fixed above the
 * user appears to rotate against the screen when the phone is rotated around its depth axis.
 * Returns 0 while [enabled] is false. The sensor only runs while the lifecycle is resumed.
 */
@Composable
internal fun rememberDeviceTilt(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val angle = remember { mutableFloatStateOf(0f) }

    DisposableEffect(enabled, context, lifecycleOwner) {
        if (!enabled) {
            angle.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = TiltListener(context) { degrees ->
            if (abs(degrees - angle.floatValue) > 0.5f) angle.floatValue = degrees
        }
        var registered = false
        fun register() {
            if (!registered) {
                registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun unregister() {
            if (registered) {
                sensorManager.unregisterListener(listener)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return angle
}

private class TiltListener(
    private val context: Context,
    private val onAngle: (Float) -> Unit
) : SensorEventListener {
    private var gx = 0f
    private var gy = 1f

    override fun onSensorChanged(event: SensorEvent) {
        var x = event.values[0]
        var y = event.values[1]
        // Map device axes to the current screen orientation.
        when (displayRotation()) {
            Surface.ROTATION_90 -> { val t = x; x = -y; y = t }
            Surface.ROTATION_180 -> { x = -x; y = -y }
            Surface.ROTATION_270 -> { val t = x; x = y; y = -t }
        }
        val length = sqrt(x * x + y * y)
        // Lying flat: the in-plane gravity is too small to give a stable direction.
        if (length < 1.5f) return
        // Low-pass the unit vector so the highlight glides instead of jittering.
        gx += (x / length - gx) * 0.5f
        gy += (y / length - gy) * 0.5f
        val n = sqrt(gx * gx + gy * gy)
        if (n < 1e-4f) return
        gx /= n
        gy /= n
        // Upright gravity reads (0, +g): 90 degrees. Rotating the phone clockwise by θ adds θ, and a
        // world-fixed light then appears rotated by -θ on screen.
        val deviceRotation = Math.toDegrees(atan2(gy, gx).toDouble()).toFloat() - 90f
        onAngle(-deviceRotation)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    }.getOrDefault(Surface.ROTATION_0)
}
