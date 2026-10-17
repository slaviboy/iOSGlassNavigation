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

package com.slaviboy.liquidglass.effect

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.glass.GlassMaterial
import com.slaviboy.liquidglass.shader.LENS_AGSL
import com.slaviboy.liquidglass.shader.PLATTER_AGSL
import kotlin.math.abs
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/** Best tier this device can render. */
internal fun deviceMaxTier(): RenderTier = when {
    Build.VERSION.SDK_INT >= 33 -> RenderTier.Full
    Build.VERSION.SDK_INT >= 31 -> RenderTier.Blur
    else -> RenderTier.Basic
}

/**
 * Caches `blur(colorMatrix(input))`, where the matrix boosts saturation and (for the Blur tier)
 * applies UIKit's luma remap. RenderEffects are immutable, so they are rebuilt only when a key
 * changes.
 */
@RequiresApi(31)
internal class FrostEffectCache {
    private val colorKey = FloatArray(4) { Float.NaN }
    private var colorEffect: RenderEffect? = null
    private var blurKey = Float.NaN
    private var blurColorEffect: RenderEffect? = null
    private var frostEffect: RenderEffect? = null
    private var composeKey: RenderEffect? = null
    private var composeEffect: ComposeRenderEffect? = null
    private val matrix = FloatArray(20)

    private fun color(saturation: Float, black: Float, white: Float, chroma: Float): RenderEffect? {
        val identity = abs(saturation - 1f) < 0.001f && black == 0f && white == 1f && chroma == 1f
        if (identity) return null
        if (saturation != colorKey[0] || black != colorKey[1] || white != colorKey[2] || chroma != colorKey[3]) {
            colorKey[0] = saturation
            colorKey[1] = black
            colorKey[2] = white
            colorKey[3] = chroma
            colorEffect = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(GlassMaterial.frostMatrix(saturation, black, white, chroma, matrix))
            )
        }
        return colorEffect
    }

    /** `blur(color(content))`, or null when both are identity. */
    fun frost(
        radiusPx: Float,
        saturation: Float,
        black: Float = 0f,
        white: Float = 1f,
        chroma: Float = 1f
    ): RenderEffect? {
        val color = color(saturation, black, white, chroma)
        if (radiusPx < 0.5f) return color
        if (radiusPx != blurKey || color !== blurColorEffect || frostEffect == null) {
            blurKey = radiusPx
            blurColorEffect = color
            frostEffect = if (color != null) {
                RenderEffect.createBlurEffect(radiusPx, radiusPx, color, Shader.TileMode.CLAMP)
            } else {
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            }
        }
        return frostEffect
    }

    fun composeFrost(
        radiusPx: Float,
        saturation: Float,
        black: Float = 0f,
        white: Float = 1f,
        chroma: Float = 1f
    ): ComposeRenderEffect? {
        val fx = frost(radiusPx, saturation, black, white, chroma) ?: return null
        if (fx !== composeKey) {
            composeKey = fx
            composeEffect = fx.asComposeRenderEffect()
        }
        return composeEffect
    }
}

/** Every uniform of the two glass programs, in layer pixels. */
internal class ShaderUniforms {
    var cx = 0f
    var cy = 0f
    var hw = 0f
    var hh = 0f
    var radius = 0f
    var clampLeft = 0f
    var clampTop = 0f
    var clampRight = 0f
    var clampBottom = 0f
    var oval = 0.5f
    var refract = 0f
    var refractHeight = 1f
    var warp = 0f
    var warpHeight = 1f
    var aberration = 0f
    var aberrationDepth = 1f
    var faceGray = 1f
    var mapBlack = 0f
    var mapWhite = 1f
    var mapChroma = 1f
    var fillGray = 1f
    var fillAlpha = 0f
    var faceAlpha = 0f
    var frostEdge = 0.46f
    var frostDepth = 1f
    var tintR = 0f
    var tintG = 0f
    var tintB = 0f
    var tintA = 0f
    var dim = 0f
    var lightX = 0.7071f
    var lightY = 0.7071f
    var rimWidth = 1f
    var rimCurvature = 0.7f
    var rimIntensity = 1f
    var specularFalloff = 2f
    var rimMat0 = 1.5f
    var rimMat1 = 0.1f
    var rimMat2 = 0.9f
    var specular = 0.5f
    var press = 0f
    var tone = 1f
    var topShade = 0f
    var topShadeDepth = 1f
    var alpha = 1f

    fun setRimMatrix(m: FloatArray) {
        rimMat0 = m[0]
        rimMat1 = m[1]
        rimMat2 = m[2]
    }

    fun pack(out: FloatArray) {
        out[0] = cx; out[1] = cy; out[2] = hw; out[3] = hh; out[4] = radius
        out[5] = clampLeft; out[6] = clampTop; out[7] = clampRight; out[8] = clampBottom
        out[9] = oval; out[10] = refract; out[11] = refractHeight; out[12] = warp; out[13] = warpHeight
        out[14] = aberration; out[15] = aberrationDepth; out[16] = faceGray; out[17] = faceAlpha
        out[18] = frostEdge; out[19] = frostDepth; out[20] = tintR; out[21] = tintG; out[22] = tintB
        out[23] = tintA; out[24] = dim; out[25] = lightX; out[26] = lightY; out[27] = rimWidth
        out[28] = rimCurvature; out[29] = rimIntensity; out[30] = specularFalloff; out[31] = rimMat0
        out[32] = rimMat1; out[33] = rimMat2; out[34] = specular; out[35] = press; out[36] = tone
        out[37] = topShade; out[38] = topShadeDepth; out[39] = alpha; out[40] = mapBlack; out[41] = mapWhite
        out[42] = mapChroma; out[43] = fillGray; out[44] = fillAlpha
    }

    companion object {
        const val SIZE = 45
    }
}

/**
 * A compiled glass program. Uniforms are copied into a RenderEffect when it is created, so a new
 * effect is built only when a uniform (or the frost input) actually changed.
 */
@RequiresApi(33)
internal class GlassProgram(private val lens: Boolean) {
    private val shader = RuntimeShader(if (lens) LENS_AGSL else PLATTER_AGSL)
    private val packed = FloatArray(ShaderUniforms.SIZE)
    private val last = FloatArray(ShaderUniforms.SIZE) { Float.NaN }
    private var lastInner: RenderEffect? = null
    private var cached: ComposeRenderEffect? = null

    fun renderEffect(u: ShaderUniforms, inner: RenderEffect?): ComposeRenderEffect {
        u.pack(packed)
        val previous = cached
        if (previous != null && inner === lastInner && packed.contentEquals(last)) return previous

        shader.setFloatUniform("uCenter", u.cx, u.cy)
        shader.setFloatUniform("uHalf", u.hw, u.hh)
        shader.setFloatUniform("uRadius", u.radius)
        shader.setFloatUniform("uClamp", u.clampLeft, u.clampTop, u.clampRight, u.clampBottom)
        shader.setFloatUniform("uOval", u.oval)
        shader.setFloatUniform("uLight", u.lightX, u.lightY)
        shader.setFloatUniform("uRim", u.rimWidth, u.rimCurvature, u.rimIntensity, u.specularFalloff)
        shader.setFloatUniform("uRimMat", u.rimMat0, u.rimMat1, u.rimMat2)
        shader.setFloatUniform("uSpecular", u.specular)
        shader.setFloatUniform("uPress", u.press)
        if (lens) {
            shader.setFloatUniform("uInner", u.refract, u.refractHeight)
            shader.setFloatUniform("uWarp", u.warp, u.warpHeight)
            shader.setFloatUniform("uAberr", u.aberration, u.aberrationDepth)
            shader.setFloatUniform("uAberrMat", ABERRATION[0], ABERRATION[1], ABERRATION[2], ABERRATION[3])
            shader.setFloatUniform("uTone", u.tone)
            shader.setFloatUniform("uTopShade", u.topShade, u.topShadeDepth)
            shader.setFloatUniform("uAlpha", u.alpha)
            shader.setFloatUniform("uFace", u.faceGray, u.faceGray, u.faceGray, u.faceAlpha)
        } else {
            shader.setFloatUniform("uRefract", u.refract, u.refractHeight)
            shader.setFloatUniform("uFrost", u.frostEdge, u.frostDepth)
            shader.setFloatUniform("uTint", u.tintR, u.tintG, u.tintB, u.tintA)
            shader.setFloatUniform("uDim", u.dim)
            shader.setFloatUniform("uMap", u.mapBlack, u.mapWhite, u.mapChroma, 0f)
            shader.setFloatUniform("uFill", u.fillGray, u.fillAlpha)
        }

        val fx = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val chained = if (inner != null) RenderEffect.createChainEffect(fx, inner) else fx
        val result = chained.asComposeRenderEffect()
        cached = result
        lastInner = inner
        packed.copyInto(last)
        return result
    }

    private companion object {
        val ABERRATION = com.slaviboy.liquidglass.config.GlassConstants.AberrationMatrix
    }
}

/** Both programs of a glass element, compiled together (off the main thread). */
internal class GlassPrograms(val platter: Any, val lens: Any?) {
    companion object {
        /** Compiles the programs; returns null on devices or drivers where AGSL fails. */
        fun compile(withLens: Boolean): GlassPrograms? {
            if (Build.VERSION.SDK_INT < 33) return null
            return try {
                GlassPrograms(GlassProgram(lens = false), if (withLens) GlassProgram(lens = true) else null)
            } catch (t: Throwable) {
                android.util.Log.w("LiquidGlass", "AGSL compilation failed, falling back to the Blur tier", t)
                null
            }
        }
    }
}
