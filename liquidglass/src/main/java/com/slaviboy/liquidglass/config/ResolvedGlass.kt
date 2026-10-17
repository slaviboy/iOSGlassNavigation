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

package com.slaviboy.liquidglass.config

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slaviboy.liquidglass.glass.GlassMaterial

/** Accessibility switches currently enabled on the device. */
internal data class SystemAccessibility(
    val reduceMotion: Boolean = false,
    val increaseContrast: Boolean = false,
    val reduceTransparency: Boolean = false
)

/** [LiquidGlassSettings] + [LiquidGlassStyle] after accessibility, variant and tier overrides. */
internal class ResolvedGlass(
    val tier: RenderTier,
    val identity: Boolean,
    val clearVariant: Boolean,
    val tinted: Boolean,
    val reduceTransparency: Boolean,
    val increaseContrast: Boolean,
    val reduceMotion: Boolean,
    val platter: GlassOptics,
    val lens: GlassOptics,
    val vibrancy: Float,
    val frost: Float,
    val specularIntensity: Float,
    val specularAngle: Float,
    val specularFalloff: Float,
    val rimWidth: Dp,
    val tintColor: Color,
    val tintIntensity: Float,
    val shadow: GlassShadowStyle,
    val lensShadow: GlassShadowStyle,
    val accentColor: Color,
    val lensMagnification: Float,
    val deviceTilt: Boolean,
    val haptics: Boolean,
    val adaptive: Boolean,
    val fixedDark: Boolean
) {
    val hasTint: Boolean get() = tintColor.isSpecified && tintIntensity > 0f
}

/** Lowest quality wins: a requested tier can only downgrade what the device supports. */
internal fun downgradeTier(requested: RenderTier, deviceMax: RenderTier): RenderTier {
    val max = if (deviceMax == RenderTier.Auto) RenderTier.Full else deviceMax
    if (requested == RenderTier.Auto) return max
    return if (requested.ordinal >= max.ordinal) requested else max
}

internal fun resolveGlass(
    settings: LiquidGlassSettings,
    style: LiquidGlassStyle,
    system: SystemAccessibility,
    deviceMax: RenderTier
): ResolvedGlass {
    val follow = settings.followSystem
    val a11y = settings.accessibility
    val reduceTransparency = a11y.reduceTransparency || (follow.reduceTransparency && system.reduceTransparency)
    val increaseContrast = a11y.increaseContrast || (follow.increaseContrast && system.increaseContrast)
    val reduceMotion = a11y.reduceMotion || (follow.reduceMotion && system.reduceMotion)

    var platter = style.platter
    var lens = style.lens
    var frost = style.frost
    var vibrancy = style.vibrancy
    var tint = style.tintColor
    val clearVariant = settings.variant == GlassVariant.Clear

    if (clearVariant) {
        platter = platter.copy(blurRadius = minOf(platter.blurRadius, 1.dp))
        frost *= 0.2f
    }
    if (reduceTransparency) {
        platter = platter.copy(
            blurRadius = GlassConstants.ReduceTransparencyBlur.dp,
            refractionAmount = 0.dp,
            chromaticAberration = 0f
        )
        lens = lens.copy(
            blurRadius = 12.dp,
            refractionAmount = 0.dp,
            warpAmount = 0.dp,
            chromaticAberration = 0f
        )
        vibrancy = 1f
    }
    if (increaseContrast) {
        tint = Color.Unspecified
        vibrancy = 1f
    }

    val accent = when {
        increaseContrast -> LiquidGlassDefaults.AccentContrast
        else -> style.accentColor
    }

    return ResolvedGlass(
        tier = downgradeTier(settings.renderTier, deviceMax),
        identity = settings.variant == GlassVariant.Identity,
        clearVariant = clearVariant,
        tinted = settings.appearance == GlassAppearance.Tinted,
        reduceTransparency = reduceTransparency,
        increaseContrast = increaseContrast,
        reduceMotion = reduceMotion,
        platter = platter,
        lens = lens,
        vibrancy = vibrancy,
        frost = frost,
        specularIntensity = style.specularIntensity,
        specularAngle = style.specularAngle,
        specularFalloff = style.specularFalloff,
        rimWidth = style.rimWidth,
        tintColor = tint,
        tintIntensity = style.tintIntensity.coerceIn(0f, 1f),
        shadow = style.shadow,
        lensShadow = style.lensShadow,
        accentColor = accent,
        lensMagnification = style.lensMagnification,
        deviceTilt = settings.deviceTiltHighlight && !reduceMotion,
        haptics = settings.haptics,
        adaptive = settings.colorScheme == GlassColorScheme.Adaptive && !reduceTransparency,
        fixedDark = settings.colorScheme == GlassColorScheme.Dark
    )
}

/**
 * How UIKit builds the glass face: the backdrop luma is remapped into a narrow range
 * [[black], [white]] that follows the average brightness behind the glass, its chroma is kept
 * (scaled by [chroma]), and a [fillGray] fill is mixed in with [fillAlpha].
 */
internal class GlassFace {
    var black = 0.47f
    var white = 1f
    var chroma = 1f
    var fillGray = 1f
    var fillAlpha = 0.5f
    var dim = 0f
    var luma = 0.6f

    /**
     * @param luma smoothed Rec.709 luma of the content behind the glass.
     * @param darkGlass whether the glass is currently dark (adaptive result or fixed scheme).
     * @param systemDark whether the system is in dark mode.
     */
    fun update(r: ResolvedGlass, luma: Float, darkGlass: Boolean, systemDark: Boolean) {
        this.luma = luma
        fillGray = if (darkGlass) 0f else 1f
        when {
            r.reduceTransparency -> {
                // Every luma maps to one frosted level; most of the color is kept.
                val level = if (darkGlass) 0.16f else GlassConstants.ReduceTransparencyLuma
                black = level
                white = level
                chroma = 0.9f / 1.1f
                fillAlpha = 0.25f
            }

            r.increaseContrast -> {
                black = GlassMaterial.faceBlack(luma)
                white = GlassMaterial.faceWhite(luma, systemDark)
                chroma = 0.8f / 1.1f
                fillAlpha = GlassConstants.IncreaseContrastFill
            }

            r.clearVariant -> {
                // Clear glass keeps the backdrop as is: no remapping, a whisper of fill.
                black = 0f
                white = 1f
                chroma = 1f
                fillAlpha = tinted(r, fillAlphaFor(luma, darkGlass) * r.frost)
            }

            else -> {
                black = GlassMaterial.faceBlack(luma)
                white = GlassMaterial.faceWhite(luma, systemDark)
                chroma = 1f
                fillAlpha = tinted(r, fillAlphaFor(luma, darkGlass) * r.frost)
            }
        }
        if (r.hasTint) fillAlpha *= 1f - r.tintIntensity
        dim = if (r.clearVariant) 0.35f * GlassMaterial.smoothstep(0.45f, 0.75f, luma) else 0f
    }

    /** Average color of the face over a backdrop of luma [l], for the tier without a backdrop. */
    fun flatGray(): Float {
        val mapped = black + (white - black) * luma
        return (mapped + (fillGray - mapped) * fillAlpha).coerceIn(0f, 1f)
    }

    /** The Tinted appearance (iOS 26.1) adds opacity on top of any variant's frost. */
    private fun tinted(r: ResolvedGlass, alpha: Float): Float {
        val a = if (r.tinted) minOf(alpha + 0.15f, 0.85f) else alpha
        return a.coerceIn(0f, 0.95f)
    }

    private fun fillAlphaFor(l: Float, darkGlass: Boolean) =
        if (darkGlass) GlassMaterial.fillAlphaDark(l) else GlassMaterial.fillAlphaLight(l)
}
