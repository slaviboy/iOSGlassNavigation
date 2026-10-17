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

package com.slaviboy.liquidglass.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A unit-mass spring. iOS describes springs either as (response, damping ratio) or as
 * (stiffness, damping coefficient); both are converted to the Compose convention here.
 */
internal class SpringSpec(val stiffness: Float, val dampingRatio: Float) {

    companion object {
        /** iOS `spring(response:dampingFraction:)`: stiffness = (2π / response)². */
        fun fromResponse(response: Float, dampingRatio: Float): SpringSpec {
            val w = 2.0 * PI / response
            return SpringSpec((w * w).toFloat(), dampingRatio)
        }

        /** iOS `CASpringAnimation(stiffness:damping:)` with mass 1: ζ = c / (2√k). */
        fun fromCoefficients(stiffness: Float, damping: Float): SpringSpec =
            SpringSpec(stiffness, (damping / (2.0 * sqrt(stiffness.toDouble()))).toFloat())
    }
}

/**
 * A value driven toward [target] by a spring. [step] uses the closed-form solution of the damped
 * harmonic oscillator, so the result does not depend on the frame rate.
 */
internal class SpringValue(
    initial: Float,
    var spec: SpringSpec,
    private val restDelta: Float = 1e-4f,
    private val restVelocity: Float = 1e-3f
) {
    var value: Float = initial
    var velocity: Float = 0f
    var target: Float = initial

    val isAtRest: Boolean
        get() = value == target && velocity == 0f

    fun snapTo(v: Float) {
        value = v
        target = v
        velocity = 0f
    }

    fun animateTo(target: Float, spec: SpringSpec) {
        this.target = target
        this.spec = spec
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        val x0 = (value - target).toDouble()
        val v0 = velocity.toDouble()
        if (x0 == 0.0 && v0 == 0.0) return
        val k = spec.stiffness.toDouble()
        val z = spec.dampingRatio.toDouble()
        val w0 = sqrt(k)
        val t = dt.toDouble()
        val x: Double
        val v: Double
        if (abs(z - 1.0) < 1e-4) {
            val b = v0 + w0 * x0
            val e = exp(-w0 * t)
            x = e * (x0 + b * t)
            v = e * (v0 - w0 * b * t)
        } else if (z < 1.0) {
            val wd = w0 * sqrt(1.0 - z * z)
            val e = exp(-z * w0 * t)
            val c = cos(wd * t)
            val s = sin(wd * t)
            val b = (v0 + z * w0 * x0) / wd
            val pos = x0 * c + b * s
            x = e * pos
            v = e * (-z * w0 * pos + (-x0 * wd * s + b * wd * c))
        } else {
            val root = w0 * sqrt(z * z - 1.0)
            val r1 = -z * w0 + root
            val r2 = -z * w0 - root
            val c2 = (v0 - r1 * x0) / (r2 - r1)
            val c1 = x0 - c2
            val e1 = exp(r1 * t)
            val e2 = exp(r2 * t)
            x = c1 * e1 + c2 * e2
            v = c1 * r1 * e1 + c2 * r2 * e2
        }
        if (abs(x) < restDelta && abs(v) < restVelocity) {
            value = target
            velocity = 0f
        } else {
            value = (target + x).toFloat()
            velocity = v.toFloat()
        }
    }
}

/** Every spring of the iOS 26 tab bar, from the UIKit measurements. */
internal object TabBarSprings {
    /** Frosted pill -> clear lens (631.65 / 50.27, critically damped, ~0.25 s). */
    val Lift = SpringSpec.fromCoefficients(631.65f, 50.27f)

    /** Lens trailing the finger (response 0.2618 s, damping 0.75). */
    val LensDrag = SpringSpec.fromResponse(0.2618f, 0.75f)

    /** Lens settling on the tab after release (response 0.405 s, damping 0.85). */
    val LensCatchUp = SpringSpec.fromResponse(0.405f, 0.85f)

    /** Press wash/glow rising (response 0.181 s, damping 0.865). */
    val PressRise = SpringSpec.fromResponse(0.181f, 0.865f)

    /** Press wash/glow falling (response 0.499 s, critically damped). */
    val PressFall = SpringSpec.fromResponse(0.499f, 1f)

    /** Bar swelling while pressed (541.5 / 29.09, ~8% overshoot). */
    val SwellRise = SpringSpec.fromCoefficients(541.5f, 29.09f)

    /** Bar returning to rest (304.6 / 20.94). */
    val SwellFall = SpringSpec.fromCoefficients(304.6f, 20.94f)

    /** Flex state springing back to rest (157.9 / 25.13). */
    val FlexReturn = SpringSpec.fromCoefficients(157.9f, 25.13f)

    /** Flex easing in (response 0.314 s, damping 1.15). */
    val FlexEase = SpringSpec.fromResponse(0.314f, 1.15f)

    /** Minimize / expand (estimated, response 0.45 s). */
    val Minimize = SpringSpec.fromResponse(0.45f, 0.86f)

    /** Minimize / expand with Reduce Motion: no overshoot. */
    val MinimizeReduced = SpringSpec.fromResponse(0.3f, 1f)
}
