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

import androidx.compose.runtime.Immutable

/** The SwiftUI `Glass` variants. */
enum class GlassVariant {
    /** Blurs and adjusts the luminosity of the backdrop; adapts to any content. The iOS default. */
    Regular,

    /**
     * Highly translucent, barely frosted; bright content behind it is dimmed by 35% to keep the
     * symbols legible. The library default.
     */
    Clear,

    /** No glass at all - content is drawn as if the effect was not applied. */
    Identity
}

/** iOS 26.1 Settings > Display & Brightness > Liquid Glass. */
enum class GlassAppearance {
    /** The original, translucent look. */
    Clear,

    /** Increases opacity and adds more contrast. */
    Tinted
}

/** Whether the glass flips between light and dark based on the content behind it. */
enum class GlassColorScheme {
    /**
     * Samples the content behind the glass and flips with the same hysteresis UIKit uses. The
     * iOS default.
     */
    Adaptive,

    /** Always light glass with dark symbols. */
    Light,

    /** Always dark glass with light symbols. The library default. */
    Dark
}

/**
 * How the glass is rendered. [Auto] picks the best tier the device supports; any other value can
 * only lower the quality (a forced [Full] on an API 31 device still renders [Blur]).
 */
enum class RenderTier {
    Auto,

    /** API 33+: AGSL refraction, chromatic dispersion and specular rim. */
    Full,

    /** API 31+: blurred, saturated backdrop with a gradient rim. No light bending. */
    Blur,

    /** Any API: frosted translucent fill with a gradient rim. */
    Basic
}

/** The iOS accessibility switches that change Liquid Glass. */
@Immutable
data class LiquidGlassAccessibility(
    /** Glass becomes frostier and hides more of the content behind it; no refraction. */
    val reduceTransparency: Boolean = false,
    /** Mostly black or white glass with a contrasting border. */
    val increaseContrast: Boolean = false,
    /** No lift, swell or flex; the selection simply slides. */
    val reduceMotion: Boolean = false
)

/**
 * Which Android system settings are mirrored into [LiquidGlassAccessibility]. A flag is effective
 * when it is set explicitly OR when it is followed here and enabled on the device.
 */
@Immutable
data class SystemFollow(
    /** Follow "Remove animations" (animator duration scale = 0). */
    val reduceMotion: Boolean = true,
    /** Follow the Android 14+ contrast setting (medium or high) or high-contrast text. */
    val increaseContrast: Boolean = true,
    /** Treat a device without cross-window blur support as "reduce transparency". */
    val reduceTransparency: Boolean = false
) {
    companion object {
        val None = SystemFollow(reduceMotion = false, increaseContrast = false, reduceTransparency = false)
    }
}

/**
 * User-facing switches, mirroring the iOS 26 settings that affect Liquid Glass.
 *
 * The defaults give clear, dark glass. For the look of iOS's own defaults use
 * `LiquidGlassSettings(variant = GlassVariant.Regular, colorScheme = GlassColorScheme.Adaptive)`.
 */
@Immutable
data class LiquidGlassSettings(
    val variant: GlassVariant = GlassVariant.Clear,
    val appearance: GlassAppearance = GlassAppearance.Clear,
    val colorScheme: GlassColorScheme = GlassColorScheme.Dark,
    val accessibility: LiquidGlassAccessibility = LiquidGlassAccessibility(),
    val followSystem: SystemFollow = SystemFollow(),
    /** Rotate the specular highlight with the device tilt, as iOS does. */
    val deviceTiltHighlight: Boolean = true,
    /** Haptic ticks when the lens crosses a tab and a confirmation on selection. */
    val haptics: Boolean = true,
    val renderTier: RenderTier = RenderTier.Auto
)
