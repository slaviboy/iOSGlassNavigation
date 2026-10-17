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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** A notification badge on a tab icon. */
@Immutable
sealed interface LiquidGlassBadge {
    /** A small dot. */
    data object Dot : LiquidGlassBadge

    /** A number, shown as "[max]+" above [max]. */
    data class Count(val count: Int, val max: Int = 99) : LiquidGlassBadge

    /** Short text, e.g. "New". */
    data class Text(val text: String) : LiquidGlassBadge
}

/**
 * One tab of a [LiquidGlassTabBar].
 *
 * @param selectedIcon shown while the tab is selected and inside the lifted lens.
 */
@Immutable
data class LiquidGlassTabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: LiquidGlassBadge? = null,
    val contentDescription: String? = label
)

/** Receiver of custom tab content in the slot overload of [LiquidGlassTabBar]. */
@Stable
interface LiquidGlassTabScope {
    val index: Int

    /** True for the selected tab, and for every tab of the accent copy seen through the lens. */
    val isSelected: Boolean

    /** Resolved color for this tab's icon and label. */
    val contentColor: Color

    /** True when this content is the accent copy that is only visible through the lifted lens. */
    val isLensCopy: Boolean

    /** 0 = expanded, 1 = minimized. Read it inside draw or graphicsLayer lambdas. */
    val minimizeProgress: Float
}
