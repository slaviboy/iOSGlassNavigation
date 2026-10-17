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

package com.slaviboy.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.slaviboy.liquidglass.config.LiquidGlassSettings
import com.slaviboy.liquidglass.config.LiquidGlassStyle
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.config.SystemAccessibility
import com.slaviboy.liquidglass.effect.deviceMaxTier
import com.slaviboy.liquidglass.system.rememberDeviceTilt
import com.slaviboy.liquidglass.system.rememberSystemAccessibility

/** Settings used by Liquid Glass components that are not given explicit settings. */
val LocalLiquidGlassSettings = compositionLocalOf { LiquidGlassSettings() }

/** Style used by Liquid Glass components that are not given an explicit style. */
val LocalLiquidGlassStyle = compositionLocalOf { LiquidGlassStyle() }

/** Device state shared by every glass element: accessibility flags, tilt and render support. */
@Stable
internal class GlassEnvironment(
    val system: State<SystemAccessibility>,
    val tilt: State<Float>,
    val deviceMaxTier: RenderTier
)

internal val LocalGlassEnvironment = staticCompositionLocalOf<GlassEnvironment?> { null }

/**
 * Provides [settings] and [style] to every Liquid Glass component inside [content], and shares a
 * single tilt sensor and accessibility observer between them.
 */
@Composable
fun LiquidGlassTheme(
    settings: LiquidGlassSettings = LiquidGlassSettings(),
    style: LiquidGlassStyle = LiquidGlassStyle(),
    content: @Composable () -> Unit
) {
    val environment = rememberGlassEnvironment(settings)
    CompositionLocalProvider(
        LocalLiquidGlassSettings provides settings,
        LocalLiquidGlassStyle provides style,
        LocalGlassEnvironment provides environment,
        content = content
    )
}

@Composable
private fun rememberGlassEnvironment(settings: LiquidGlassSettings): GlassEnvironment {
    val system = rememberSystemAccessibility()
    val reduceMotion = settings.accessibility.reduceMotion ||
        (settings.followSystem.reduceMotion && system.value.reduceMotion)
    val tilt = rememberDeviceTilt(enabled = settings.deviceTiltHighlight && !reduceMotion)
    return remember(system, tilt) { GlassEnvironment(system, tilt, deviceMaxTier()) }
}

/** The environment provided by [LiquidGlassTheme], or a private one when used without it. */
@Composable
internal fun currentGlassEnvironment(settings: LiquidGlassSettings): GlassEnvironment =
    LocalGlassEnvironment.current ?: rememberGlassEnvironment(settings)
