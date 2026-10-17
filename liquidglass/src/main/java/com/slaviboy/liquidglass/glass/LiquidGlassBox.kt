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

package com.slaviboy.liquidglass.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.slaviboy.liquidglass.backdrop.LiquidGlassBackdrop
import com.slaviboy.liquidglass.config.LiquidGlassSettings
import com.slaviboy.liquidglass.config.LiquidGlassStyle

/**
 * Color for icons and text drawn on Liquid Glass: black on light glass, white on dark or darkly
 * tinted glass. Provided by [LiquidGlassBox]; [Color.Unspecified] elsewhere.
 */
val LocalLiquidGlassContentColor = compositionLocalOf { Color.Unspecified }

/**
 * A [Box] made of Liquid Glass. Its content receives [LocalLiquidGlassContentColor], which follows
 * the adaptive light/dark glass the way iOS symbols do.
 */
@Composable
fun LiquidGlassBox(
    backdrop: LiquidGlassBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    style: LiquidGlassStyle? = null,
    settings: LiquidGlassSettings? = null,
    interactive: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    var darkGlass by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.liquidGlass(
            backdrop = backdrop,
            shape = shape,
            style = style,
            settings = settings,
            interactive = interactive,
            onDarkGlassChange = { darkGlass = it }
        ),
        contentAlignment = contentAlignment
    ) {
        CompositionLocalProvider(
            LocalLiquidGlassContentColor provides if (darkGlass) Color.White else Color.Black
        ) {
            content()
        }
    }
}
