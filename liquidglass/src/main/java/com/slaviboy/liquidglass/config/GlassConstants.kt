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

/**
 * Optical and physical constants of the iOS 26 Liquid Glass tab bar.
 *
 * The numbers were measured frame by frame from UIKit (iOS 26.5 simulator) and published by the
 * Liquidum project (MIT, https://github.com/mohamedalsamrai/liquidum). iOS points map 1:1 to dp.
 * Values marked "estimated" are not part of that measurement and were tuned by eye.
 */
internal object GlassConstants {

    // ---- Geometry (dp) ----
    const val BarHeight = 62f
    const val BarMargin = 21f
    const val BarInset = 4f
    const val LensGrow = 8f
    const val MinimizedSelection = 48f
    const val MinimizedSize = MinimizedSelection + 2 * BarInset
    const val SelectedContentScale = 1.16f

    // ---- Timing (seconds) ----
    const val LiftDelay = 0.015f
    const val LensHangTime = 0.22f
    const val CatchUpDelay = 0.024f
    const val LiftTimeout = 0.6f

    // ---- Press feedback ----
    const val Swell = 0.0355f
    const val StretchPerTravel = 2.77e-5f
    const val StretchPerOutside = 8.2e-5f
    const val StretchMax = 0.08f
    const val StretchDrift = 1.075f
    const val PressWash = 0.8448f
    const val GlowDiameter = 93f
    const val GlowOpacity = 0.2845f

    // ---- Lens flex (soft body) ----
    const val FlexLimit = 0.46f
    const val FlexScaleX = 0.6f
    const val FlexDrift = -14.8f
    const val FlexSmoothing = 0.020f
    const val FlexGain = 3.0e-4f // estimated: kick per dp/s of speed change
    const val FlexMinScaleY = 0.6f
    const val FlexMaxScaleY = 1.28f

    // ---- Platter optics (dp) ----
    const val PlatterRefraction = 49.6f
    const val PlatterRefractionHeight = 15.5f
    const val FrostEdge = 0.46f
    const val FrostFullDepth = 31f
    const val PlatterRimCurvature = 0.7f
    const val Ovalization = 0.5f

    // ---- Lens optics (dp) ----
    const val LensInner = 17.5f
    const val LensInnerHeight = 7f
    const val LensWarp = 9f
    const val LensWarpHeight = 36f
    const val LensTone = 0.97f
    const val AberrationDepth = 14f
    const val LensTopShade = 0.06f
    const val LensTopShadeDepth = 15f
    const val LensRimCurvature = 0.8f

    /** Column-major 2x2 matrix that spreads the 7 dispersion taps (UIKit kAberration). */
    val AberrationMatrix = floatArrayOf(-1.499f, 2.235f, 5.925f, 0.706f)

    // ---- Rim "vibrant" matrices: (chroma gain, luma weight, lift) ----
    val PlatterRimLight = floatArrayOf(1.5f, 0.1f, 0.9f)
    val PlatterRimDark = floatArrayOf(3.0f, 1.35f, 0.15f)
    val LensRimLight = floatArrayOf(1.5f, 0.4374f, 1.125f)
    val LensRimDark = floatArrayOf(1.5f, 0.5f, 0.75f)
    val PillLight = floatArrayOf(1.2f, 1.1294f, -0.2f)
    val PillDark = floatArrayOf(1.116f, 0.958f, 0.135f)

    // ---- Shadows (dp) ----
    const val PlatterShadowOffset = 8f
    const val PlatterShadowRadius = 24f
    const val PlatterShadowAlpha = 0.12f
    const val LensShadowOffset = 7f
    const val LensShadowRadius = 9f
    const val LensShadowAlpha = 0.1086f

    // ---- Adaptive light/dark (Rec.709 luma of the content behind the bar) ----
    const val LumaQuantization = 32f
    const val LightModeToDark = 0.210f
    const val LightModeToLight = 0.492f
    const val DarkModeToDark = 0.584f
    const val DarkModeToLight = 0.867f
    const val SampleGrowSides = 21f
    const val SampleGrowTop = 12.4f
    const val SampleIntervalMs = 200L

    // ---- Accessibility ----
    const val ReduceTransparencyBlur = 36.9f
    const val ReduceTransparencyLuma = 0.9f
    const val IncreaseContrastFill = 0.8f

    // ---- Colors ----
    const val AccentLight = 0xFF0088FF
    const val AccentDark = 0xFF0091FF
    const val AccentContrast = 0xFF1E6EF4
    const val BadgeRed = 0xFFFF3B30
}
