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

import androidx.compose.ui.graphics.Color

/** Pure color math shared by the shaders (mirrored in AGSL) and the lower render tiers. */
internal object GlassMaterial {

    const val LumaR = 0.2126f
    const val LumaG = 0.7152f
    const val LumaB = 0.0722f

    fun luma(r: Float, g: Float, b: Float): Float = LumaR * r + LumaG * g + LumaB * b

    fun luma(c: Color): Float = luma(c.red, c.green, c.blue)

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Face gray of dark glass for the backdrop luma [l] (UIKit). */
    fun faceBlack(l: Float): Float = (1.032f * l - 0.15f).coerceIn(0.1f, 0.85f)

    /** Face gray of light glass for the backdrop luma [l] (UIKit). */
    fun faceWhite(l: Float, systemDark: Boolean): Float =
        (1.032f * l - 0.15f + 0.6f).coerceIn(0f, if (systemDark) 0.9f else 1.03f)

    fun fillAlphaLight(l: Float): Float = (0.75f - 0.517f * l).coerceIn(0.25f, 0.6f)

    fun fillAlphaDark(l: Float): Float = (0.25f + 0.367f * l).coerceIn(0.25f, 0.5f)

    /**
     * 4x5 Android color matrix (offsets in 0..255) for `vibrant(c) = g(c - L) + wL + lift`, where
     * `m = (g, w, lift)`. This is the "vibrant" filter UIKit applies to rims and the resting pill.
     */
    fun vibrantMatrix(m: FloatArray, out: FloatArray = FloatArray(20)): FloatArray {
        val g = m[0]
        val k = m[1] - m[0]
        val t = m[2] * 255f
        for (row in 0 until 3) {
            val o = row * 5
            out[o] = k * LumaR + if (row == 0) g else 0f
            out[o + 1] = k * LumaG + if (row == 1) g else 0f
            out[o + 2] = k * LumaB + if (row == 2) g else 0f
            out[o + 3] = 0f
            out[o + 4] = t
        }
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /** Saturation matrix (Rec.709 weights) with an optional press wash `c + k(0.2(c - L) + 0.05)`. */
    fun saturationMatrix(saturation: Float, wash: Float = 0f, out: FloatArray = FloatArray(20)): FloatArray {
        val s = saturation
        val inv = 1f - s
        val k = wash
        val gain = 1f + 0.2f * k
        val lumaK = -0.2f * k
        val t = 0.05f * k * 255f
        val lw = floatArrayOf(LumaR, LumaG, LumaB)
        for (row in 0 until 3) {
            val o = row * 5
            for (col in 0 until 3) {
                // saturation: inv*L_col + s*identity ; then wash: gain*x + lumaK*L(x)
                val sat = inv * lw[col] + if (row == col) s else 0f
                // L(sat(c)) = L(c) because saturation preserves luma
                out[o + col] = gain * sat + lumaK * lw[col]
            }
            out[o + 3] = 0f
            out[o + 4] = t
        }
        out[15] = 0f; out[16] = 0f; out[17] = 0f; out[18] = 1f; out[19] = 0f
        return out
    }

    /**
     * Saturation followed by UIKit's face remap `black + (white - black)L + chroma(c - L)`: the
     * luma is squeezed into [black]..[white] while the chroma is kept.
     */
    fun frostMatrix(
        saturation: Float,
        black: Float,
        white: Float,
        chroma: Float,
        out: FloatArray = FloatArray(20)
    ): FloatArray {
        saturationMatrix(saturation, 0f, out)
        val k = white - black - chroma
        val lw = floatArrayOf(LumaR, LumaG, LumaB)
        for (row in 0 until 3) {
            val o = row * 5
            for (col in 0 until 3) {
                // L(sat(c)) = L(c), so the luma term can use the input weights directly.
                out[o + col] = chroma * out[o + col] + k * lw[col]
            }
            out[o + 4] = black * 255f
        }
        return out
    }

    /** Applies a 4x5 matrix to an opaque color in 0..1 space (used by tests). */
    fun apply(matrix: FloatArray, r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[4] / 255f,
        matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[9] / 255f,
        matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[14] / 255f
    )
}
