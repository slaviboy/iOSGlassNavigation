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

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A rounded rectangle described by its center, half size and corner radius (pixels). */
internal class GlassRect {
    var cx = 0f
    var cy = 0f
    var hw = 0f
    var hh = 0f
    var r = 0f

    val left get() = cx - hw
    val top get() = cy - hh
    val right get() = cx + hw
    val bottom get() = cy + hh
    val isEmpty get() = hw <= 0.5f || hh <= 0.5f

    fun set(cx: Float, cy: Float, hw: Float, hh: Float, r: Float = min(hw, hh)): GlassRect {
        this.cx = cx
        this.cy = cy
        this.hw = max(hw, 0f)
        this.hh = max(hh, 0f)
        this.r = r.coerceIn(0f, min(this.hw, this.hh))
        return this
    }

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float, r: Float) =
        set((left + right) / 2f, (top + bottom) / 2f, (right - left) / 2f, (bottom - top) / 2f, r)
}

/** Integer pixel frame of an offscreen layer, in the coordinates of the drawing node. */
internal class GlassFrame {
    var left = 0
    var top = 0
    var width = 0
    var height = 0
    val isEmpty get() = width <= 0 || height <= 0
}

/** Kotlin mirror of the AGSL helpers, used by the lower tiers and by unit tests. */
internal object GlassGeometry {

    /** Signed distance to a rounded box (Inigo Quilez); negative inside. */
    fun sdRoundRect(px: Float, py: Float, hw: Float, hh: Float, r: Float): Float {
        val qx = abs(px) - hw + r
        val qy = abs(py) - hh + r
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
    }

    /** Outward unit normal of the rounded box at a point (the SDF gradient). */
    fun gradRoundRect(px: Float, py: Float, hw: Float, hh: Float, r: Float, out: FloatArray = FloatArray(2)): FloatArray {
        val qx = abs(px) - hw + r
        val qy = abs(py) - hh + r
        var gx: Float
        var gy: Float
        if (qx > 0f && qy > 0f) {
            val len = sqrt(qx * qx + qy * qy)
            gx = qx / len
            gy = qy / len
        } else if (qx > qy) {
            gx = 1f; gy = 0f
        } else {
            gx = 0f; gy = 1f
        }
        if (px < 0f) gx = -gx
        if (py < 0f) gy = -gy
        out[0] = gx
        out[1] = gy
        return out
    }

    /** UIKit's displacement profile: the sag of a circular arc, 1 at the rim and 0 [h] inside. */
    fun sag(depth: Float, h: Float): Float {
        if (h <= 0f) return 0f
        val u = (1f - depth / h).coerceIn(0f, 1f)
        return 1f - sqrt(max(1f - u * u, 0f))
    }

    /** Soft limit: values past [min]..[max] approach the bound + [limit] asymptotically. */
    fun rubberBand(value: Float, min: Float, max: Float, limit: Float): Float = when {
        value < min -> min - limit * (1f - exp(-(min - value) / limit))
        value > max -> max + limit * (1f - exp(-(value - max) / limit))
        else -> value
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
