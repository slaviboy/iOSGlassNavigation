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

package com.slaviboy.liquidglass.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/**
 * Soft drop shadow of a rounded rect that never darkens the (translucent) glass itself.
 *
 * The shadow is an analytic Gaussian blur of the rounded rect, `0.5 * erfc(d / (σ√2))` of the
 * distance d to the edge, drawn as nine gradient slices (four radial corners, four linear edges and
 * the center). Nothing is rasterized, so it costs the same on every frame and every API level.
 */
internal class GlassShadowPainter {
    private var keyRadius = Float.NaN
    private var keySigma = Float.NaN
    private var keyAlpha = Float.NaN
    private val colors = Array(STOPS) { Color.Transparent }
    private val stops = Array(STOPS) { 0f to Color.Transparent }
    private var centerColor = Color.Transparent

    fun draw(scope: DrawScope, rect: GlassRect, cutout: Path, offsetY: Float, blurRadius: Float, alpha: Float) {
        if (alpha <= 0.001f || rect.isEmpty) return
        // Same radius-to-sigma convention as Android's blur mask filter.
        val sigma = if (blurRadius > 0f) blurRadius * 0.57735f + 0.5f else 0.5f
        val r = rect.r
        val reach = r + 3f * sigma
        if (r != keyRadius || sigma != keySigma || alpha != keyAlpha) {
            keyRadius = r
            keySigma = sigma
            keyAlpha = alpha
            for (i in 0 until STOPS) {
                val t = i / (STOPS - 1f)
                val d = t * reach - r
                colors[i] = Color.Black.copy(alpha = alpha * profile(d, sigma))
                stops[i] = t to colors[i]
            }
            centerColor = colors[0]
        }

        val left = rect.left
        val top = rect.top + offsetY
        val right = rect.right
        val bottom = rect.bottom + offsetY
        val x0 = left + r
        val x1 = right - r
        val y0 = top + r
        val y1 = bottom - r
        val outer = 3f * sigma

        scope.clipPath(cutout, ClipOp.Difference) {
            // Corners.
            corner(x0, y0, left - outer, top - outer, x0, y0, reach)
            corner(x1, y0, x1, top - outer, right + outer, y0, reach)
            corner(x0, y1, left - outer, y1, x0, bottom + outer, reach)
            corner(x1, y1, x1, y1, right + outer, bottom + outer, reach)
            // Edges.
            edge(Offset(0f, y0), Offset(0f, y0 - reach), x0, top - outer, x1, y0)
            edge(Offset(0f, y1), Offset(0f, y1 + reach), x0, y1, x1, bottom + outer)
            edge(Offset(x0, 0f), Offset(x0 - reach, 0f), left - outer, y0, x0, y1)
            edge(Offset(x1, 0f), Offset(x1 + reach, 0f), x1, y0, right + outer, y1)
            // Center.
            if (x1 > x0 && y1 > y0) {
                drawRect(centerColor, Offset(x0, y0), Size(x1 - x0, y1 - y0))
            }
        }
    }

    private fun DrawScope.corner(cx: Float, cy: Float, l: Float, t: Float, r: Float, b: Float, reach: Float) {
        if (r <= l || b <= t) return
        drawRect(
            Brush.radialGradient(*stops, center = Offset(cx, cy), radius = reach),
            Offset(l, t),
            Size(r - l, b - t)
        )
    }

    private fun DrawScope.edge(start: Offset, end: Offset, l: Float, t: Float, r: Float, b: Float) {
        if (r <= l || b <= t) return
        drawRect(Brush.linearGradient(*stops, start = start, end = end), Offset(l, t), Size(r - l, b - t))
    }

    private companion object {
        const val STOPS = 10

        /** Coverage of a blurred edge at signed distance [d] (positive outside). */
        fun profile(d: Float, sigma: Float): Float = 0.5f * erfc(d / (sigma * 1.4142135f))

        /** Abramowitz & Stegun 7.1.26, |error| < 1.5e-7. */
        fun erfc(x: Float): Float {
            val z = abs(x)
            val t = 1f / (1f + 0.3275911f * z)
            val poly = t * (0.25482959f + t * (-0.28449672f + t * (1.4214138f + t * (-1.4531521f + t * 1.0614054f))))
            val r = poly * exp(-z * z)
            return if (x >= 0f) r else 2f - r
        }
    }
}

/**
 * Specular rim for the tiers without shaders: a sweep gradient whose brightness follows
 * `|cos(θ - light)|^falloff`, stroked just inside the edge.
 */
internal class RimPainter {
    private var keyAngle = Float.NaN
    private var keyFalloff = Float.NaN
    private var keyAlpha = Float.NaN
    private var keyColor = Color.Unspecified
    private var keyCx = Float.NaN
    private var keyCy = Float.NaN
    private var brush: Brush? = null
    private val stops = Array(13) { 0f to Color.Transparent }

    fun draw(
        scope: DrawScope, rect: GlassRect, path: Path, lightAngleRad: Float, falloff: Float,
        alpha: Float, widthPx: Float, color: Color = Color.White
    ) {
        if (alpha <= 0.001f || widthPx <= 0f || rect.isEmpty) return
        if (lightAngleRad != keyAngle || falloff != keyFalloff || alpha != keyAlpha || color != keyColor ||
            rect.cx != keyCx || rect.cy != keyCy || brush == null
        ) {
            keyAngle = lightAngleRad
            keyFalloff = falloff
            keyAlpha = alpha
            keyColor = color
            keyCx = rect.cx
            keyCy = rect.cy
            for (i in 0 until 13) {
                val t = i / 12f
                val theta = t * 2f * PI.toFloat()
                val intensity = abs(cos(theta - lightAngleRad)).pow(falloff)
                stops[i] = t to color.copy(alpha = (alpha * (0.25f + 0.75f * intensity)).coerceIn(0f, 1f))
            }
            brush = Brush.sweepGradient(*stops, center = Offset(rect.cx, rect.cy))
        }
        scope.clipPath(path) {
            drawPath(path, brush!!, style = Stroke(width = widthPx * 2f))
        }
    }
}

/** The fingertip glow: a blurred white disk, approximated by a radial gradient. */
internal class GlowPainter {
    private var radiusKey = -1f
    private var brush: Brush? = null

    fun draw(scope: DrawScope, x: Float, y: Float, radiusPx: Float, scale: Float, alpha: Float) {
        if (alpha <= 0.001f || radiusPx <= 0f) return
        if (radiusPx != radiusKey || brush == null) {
            radiusKey = radiusPx
            brush = Brush.radialGradient(
                0f to Color.White,
                0.3f to Color.White.copy(alpha = 0.8f),
                0.6f to Color.White.copy(alpha = 0.3f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = radiusPx
            )
        }
        with(scope) {
            translate(x, y) {
                scale(scale, scale, pivot = Offset.Zero) {
                    drawCircle(brush!!, radiusPx, Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
                }
            }
        }
    }
}
