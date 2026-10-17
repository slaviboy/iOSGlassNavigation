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

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.slaviboy.liquidglass.config.SystemAccessibility
import java.util.function.Consumer

/**
 * Android counterparts of the iOS accessibility switches:
 * - Reduce Motion: "Remove animations" (animator duration scale = 0).
 * - Increase Contrast: the Android 14 contrast setting (medium/high), or high-contrast text.
 * - Reduce Transparency: no cross-window blur support (opt-in, see [com.slaviboy.liquidglass.config.SystemFollow]).
 */
@Composable
internal fun rememberSystemAccessibility(): State<SystemAccessibility> {
    val context = LocalContext.current.applicationContext
    val state = remember(context) { mutableStateOf(readSystemAccessibility(context)) }
    DisposableEffect(context) {
        val refresh = { state.value = readSystemAccessibility(context) }
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        runCatching {
            resolver.registerContentObserver(Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST), false, observer)
        }

        var contrastListener: Any? = null
        if (Build.VERSION.SDK_INT >= 34) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager != null) {
                val listener = UiModeManager.ContrastChangeListener { refresh() }
                uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
                contrastListener = listener
            }
        }
        var blurListener: Consumer<Boolean>? = null
        if (Build.VERSION.SDK_INT >= 31) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager != null) {
                val listener = Consumer<Boolean> { refresh() }
                runCatching { windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }
                blurListener = listener
            }
        }

        onDispose {
            resolver.unregisterContentObserver(observer)
            if (Build.VERSION.SDK_INT >= 34 && contrastListener != null) {
                (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
                    ?.removeContrastChangeListener(contrastListener as UiModeManager.ContrastChangeListener)
            }
            if (Build.VERSION.SDK_INT >= 31 && blurListener != null) {
                runCatching {
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                        ?.removeCrossWindowBlurEnabledListener(blurListener)
                }
            }
        }
    }
    return state
}

private const val HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"

internal fun readSystemAccessibility(context: Context): SystemAccessibility {
    val resolver = context.contentResolver
    val animatorScale = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    var contrast = runCatching { Settings.Secure.getInt(resolver, HIGH_TEXT_CONTRAST, 0) == 1 }.getOrDefault(false)
    if (Build.VERSION.SDK_INT >= 34) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager != null && uiModeManager.contrast >= 0.5f) contrast = true
    }

    var noBlur = false
    if (Build.VERSION.SDK_INT >= 31) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        noBlur = windowManager != null && !windowManager.isCrossWindowBlurEnabled
    }

    return SystemAccessibility(
        reduceMotion = animatorScale == 0f,
        increaseContrast = contrast,
        reduceTransparency = noBlur
    )
}
