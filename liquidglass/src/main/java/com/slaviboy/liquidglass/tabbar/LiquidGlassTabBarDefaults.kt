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

package com.slaviboy.liquidglass.tabbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.config.LiquidGlassDefaults
import com.slaviboy.liquidglass.config.ResolvedGlass
import com.slaviboy.liquidglass.glass.GlassMaterial

/** Sizes of the tab bar. Defaults are the measured iOS 26 values. */
@Immutable
data class LiquidGlassTabBarDimensions(
    val height: Dp = GlassConstants.BarHeight.dp,
    val horizontalMargin: Dp = GlassConstants.BarMargin.dp,
    val bottomMargin: Dp = GlassConstants.BarMargin.dp,
    val inset: Dp = GlassConstants.BarInset.dp,
    val lensGrow: Dp = GlassConstants.LensGrow.dp,
    val minimizedSize: Dp = GlassConstants.MinimizedSize.dp,
    val iconSize: Dp = 24.dp,
    val labelSize: TextUnit = 10.sp
)

/**
 * Content colors. [Color.Unspecified] means automatic: iOS system blue for the selected tab and
 * black/white (following the adaptive glass) for the others; white on a dark tinted bar.
 */
@Immutable
data class LiquidGlassTabBarColors(
    val selectedContentColor: Color = Color.Unspecified,
    val unselectedContentColor: Color = Color.Unspecified,
    val badgeColor: Color = Color(GlassConstants.BadgeRed),
    val badgeContentColor: Color = Color.White
)

object LiquidGlassTabBarDefaults {

    fun dimensions(
        height: Dp = GlassConstants.BarHeight.dp,
        horizontalMargin: Dp = GlassConstants.BarMargin.dp,
        bottomMargin: Dp = GlassConstants.BarMargin.dp,
        inset: Dp = GlassConstants.BarInset.dp,
        lensGrow: Dp = GlassConstants.LensGrow.dp,
        minimizedSize: Dp = GlassConstants.MinimizedSize.dp,
        iconSize: Dp = 24.dp,
        labelSize: TextUnit = 10.sp
    ) = LiquidGlassTabBarDimensions(
        height, horizontalMargin, bottomMargin, inset, lensGrow, minimizedSize, iconSize, labelSize
    )

    fun colors(
        selectedContentColor: Color = Color.Unspecified,
        unselectedContentColor: Color = Color.Unspecified,
        badgeColor: Color = Color(GlassConstants.BadgeRed),
        badgeContentColor: Color = Color.White
    ) = LiquidGlassTabBarColors(selectedContentColor, unselectedContentColor, badgeColor, badgeContentColor)

    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.navigationBars

    /** Distance from the bottom of the screen to the top of the bar. */
    @Composable
    fun bottomOffset(
        dimensions: LiquidGlassTabBarDimensions = dimensions(),
        windowInsets: WindowInsets = this.windowInsets
    ): Dp = max(dimensions.bottomMargin, windowInsets.asPaddingValues().calculateBottomPadding() + 4.dp)

    /** Bottom content padding so scrollable content can scroll fully above the bar. */
    @Composable
    fun contentBottomPadding(
        dimensions: LiquidGlassTabBarDimensions = dimensions(),
        windowInsets: WindowInsets = this.windowInsets
    ): Dp = dimensions.height + bottomOffset(dimensions, windowInsets) + 8.dp
}

/** Automatic content colors for the current glass. */
internal fun resolveContentColors(
    colors: LiquidGlassTabBarColors,
    resolved: ResolvedGlass,
    darkGlass: Boolean,
    out: Array<Color>
) {
    val tintedDark = resolved.hasTint && resolved.tintIntensity > 0.4f && GlassMaterial.luma(resolved.tintColor) < 0.6f
    val tintedLight = resolved.hasTint && resolved.tintIntensity > 0.4f && !tintedDark
    val selected = when {
        colors.selectedContentColor.isSpecified -> colors.selectedContentColor
        tintedDark -> Color.White
        tintedLight -> Color.Black
        resolved.accentColor.isSpecified -> resolved.accentColor
        darkGlass -> LiquidGlassDefaults.AccentDark
        else -> LiquidGlassDefaults.AccentLight
    }
    val unselected = when {
        colors.unselectedContentColor.isSpecified -> colors.unselectedContentColor
        tintedDark -> Color.White.copy(alpha = if (resolved.increaseContrast) 1f else 0.8f)
        tintedLight -> Color.Black.copy(alpha = 0.75f)
        darkGlass -> Color.White
        resolved.increaseContrast -> Color.Black
        else -> Color.Black.copy(alpha = 0.88f)
    }
    out[0] = selected
    out[1] = unselected
}
