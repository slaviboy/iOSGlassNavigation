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

package com.slaviboy.liquidglass.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.slaviboy.liquidglass.config.GlassColorScheme
import com.slaviboy.liquidglass.config.GlassConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Whether a glass element is currently light or dark, plus the smoothed luma behind it. */
@Stable
internal class AdaptiveGlassState(systemDark: Boolean) {
    internal val controller = AdaptiveAppearance(systemDark)

    var isDark by mutableStateOf(systemDark)
        internal set

    /** Glass colors follow the luma on a 0.5 s critically damped spring (UIKit). */
    val luma = Animatable(defaultLuma(systemDark))

    companion object {
        fun defaultLuma(dark: Boolean) = if (dark) 0.3f else 0.6f
    }
}

/**
 * Keeps [AdaptiveGlassState] up to date. With [GlassColorScheme.Adaptive] it samples the content
 * behind the glass every 200 ms while the lifecycle is started and flips light/dark; with a fixed
 * scheme the glass never flips, but the luma is still sampled when [sampleLuma] is set (Clear glass
 * dims bright content behind it).
 */
@Composable
internal fun rememberAdaptiveGlass(
    scheme: GlassColorScheme,
    adaptive: Boolean,
    systemDark: Boolean,
    sampleLuma: Boolean,
    sample: suspend () -> Float?
): AdaptiveGlassState {
    val state = remember { AdaptiveGlassState(systemDark) }
    val currentSample by rememberUpdatedState(sample)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(scheme, adaptive, systemDark, sampleLuma, lifecycleOwner) {
        state.controller.systemDark = systemDark
        val flips = adaptive && scheme == GlassColorScheme.Adaptive
        if (flips) {
            state.isDark = state.controller.isDark
        } else {
            val dark = when (scheme) {
                GlassColorScheme.Light -> false
                GlassColorScheme.Dark -> true
                GlassColorScheme.Adaptive -> systemDark
            }
            state.isDark = dark
            if (!sampleLuma) {
                state.luma.snapTo(AdaptiveGlassState.defaultLuma(dark))
                return@LaunchedEffect
            }
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val l = runCatching { currentSample() }.getOrNull()
                if (l != null) {
                    if (flips) state.isDark = state.controller.update(l)
                    val target = AdaptiveAppearance.quantize(l)
                    if (target != state.luma.targetValue) {
                        launch { state.luma.animateTo(target, spring(dampingRatio = 1f, stiffness = 157.9f)) }
                    }
                }
                delay(GlassConstants.SampleIntervalMs)
            }
        }
    }
    return state
}
