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

import com.slaviboy.liquidglass.config.GlassConstants
import kotlin.math.roundToInt

/**
 * UIKit flips small glass (tab bars, nav bars) between light and dark from the mean luma of the
 * content behind it. The luma is quantized to 1/32 and switched with hysteresis so the glass does
 * not flicker over mid-gray content.
 */
internal class AdaptiveAppearance(systemDark: Boolean) {

    var systemDark: Boolean = systemDark
        set(value) {
            if (field != value) {
                field = value
                isDark = value
            }
        }

    var isDark: Boolean = systemDark
        private set

    /** Feeds a new luma sample and returns whether the glass is dark afterwards. */
    fun update(luma: Float): Boolean {
        val l = quantize(luma)
        val toDark = if (systemDark) GlassConstants.DarkModeToDark else GlassConstants.LightModeToDark
        val toLight = if (systemDark) GlassConstants.DarkModeToLight else GlassConstants.LightModeToLight
        if (isDark && l > toLight) {
            isDark = false
        } else if (!isDark && l < toDark) {
            isDark = true
        }
        return isDark
    }

    companion object {
        fun quantize(luma: Float): Float {
            val q = GlassConstants.LumaQuantization
            return (luma.coerceIn(0f, 1f) * q).roundToInt() / q
        }
    }
}
