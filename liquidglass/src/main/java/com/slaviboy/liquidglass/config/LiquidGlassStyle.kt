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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a piece of glass bends and frosts the light behind it.
 *
 * @param blurRadius frost of the backdrop before it is refracted.
 * @param refractionAmount how far (at the very rim) the backdrop is pulled in from the edge.
 * @param refractionHeight width of the curved bezel; deeper than this the glass is flat.
 * @param warpAmount outward warp near the rim (lens only), giving the lifted droplet its bulge.
 * @param warpHeight how far in from the rim the warp reaches.
 * @param chromaticAberration strength of the color fringes at the rim (0 = none, 1 = iOS).
 */
@Immutable
data class GlassOptics(
    val blurRadius: Dp,
    val refractionAmount: Dp,
    val refractionHeight: Dp,
    val warpAmount: Dp = 0.dp,
    val warpHeight: Dp = 0.dp,
    val chromaticAberration: Float = 0f
)

/** A soft drop shadow under the glass. The shadow never darkens the glass itself. */
@Immutable
data class GlassShadowStyle(
    val offsetY: Dp,
    val radius: Dp,
    val alpha: Float
) {
    companion object {
        val None = GlassShadowStyle(0.dp, 0.dp, 0f)
    }
}

/**
 * The look of the glass. Defaults reproduce the iOS 26 tab bar; every value can be tuned.
 *
 * @param platter optics of the bar itself.
 * @param lens optics of the clear droplet that lifts out of the bar while pressing.
 * @param vibrancy saturation boost of the backdrop seen through the glass (1 = none).
 * @param frost multiplier of the frosted face fill: 0 = crystal clear, 1 = iOS, 2 = milky.
 * @param specularIntensity brightness of the specular rim highlight.
 * @param specularAngle direction of the light axis in degrees (0 = horizontal, 45 = from top-left).
 * @param specularFalloff how tightly the highlight hugs the light direction.
 * @param rimWidth width of the lit rim band.
 * @param tintColor optional glass tint, mapped to the brightness of the content underneath.
 * @param tintIntensity 0..1 strength of [tintColor].
 * @param shadow drop shadow of the bar.
 * @param lensShadow drop shadow of the lifted lens.
 * @param accentColor color of the selected tab. Unspecified = iOS system blue.
 * @param lensMagnification scale of the tab content seen through the lifted lens.
 */
@Immutable
data class LiquidGlassStyle(
    val platter: GlassOptics = LiquidGlassDefaults.PlatterOptics,
    val lens: GlassOptics = LiquidGlassDefaults.LensOptics,
    val vibrancy: Float = 1.5f,
    val frost: Float = 1f,
    val specularIntensity: Float = 1f,
    val specularAngle: Float = 45f,
    val specularFalloff: Float = 2f,
    val rimWidth: Dp = 1.5.dp,
    val tintColor: Color = Color.Unspecified,
    val tintIntensity: Float = 0.85f,
    val shadow: GlassShadowStyle = LiquidGlassDefaults.Shadow,
    val lensShadow: GlassShadowStyle = LiquidGlassDefaults.LensShadow,
    val accentColor: Color = Color.Unspecified,
    val lensMagnification: Float = GlassConstants.SelectedContentScale
)

object LiquidGlassDefaults {
    val PlatterOptics = GlassOptics(
        blurRadius = 8.dp,
        refractionAmount = GlassConstants.PlatterRefraction.dp,
        refractionHeight = GlassConstants.PlatterRefractionHeight.dp
    )
    val LensOptics = GlassOptics(
        blurRadius = 0.dp,
        refractionAmount = GlassConstants.LensInner.dp,
        refractionHeight = GlassConstants.LensInnerHeight.dp,
        warpAmount = GlassConstants.LensWarp.dp,
        warpHeight = GlassConstants.LensWarpHeight.dp,
        chromaticAberration = 1f
    )
    val Shadow = GlassShadowStyle(
        offsetY = GlassConstants.PlatterShadowOffset.dp,
        radius = GlassConstants.PlatterShadowRadius.dp,
        alpha = GlassConstants.PlatterShadowAlpha
    )
    val LensShadow = GlassShadowStyle(
        offsetY = GlassConstants.LensShadowOffset.dp,
        radius = GlassConstants.LensShadowRadius.dp,
        alpha = GlassConstants.LensShadowAlpha
    )

    /** iOS system blue on light glass. */
    val AccentLight = Color(GlassConstants.AccentLight)

    /** iOS system blue on dark glass. */
    val AccentDark = Color(GlassConstants.AccentDark)

    /** iOS system blue with Increase Contrast on. */
    val AccentContrast = Color(GlassConstants.AccentContrast)
}
