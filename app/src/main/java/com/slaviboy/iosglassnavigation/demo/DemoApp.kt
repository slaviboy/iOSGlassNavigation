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

package com.slaviboy.iosglassnavigation.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slaviboy.liquidglass.LiquidGlassTheme
import com.slaviboy.liquidglass.backdrop.liquidGlassBackdrop
import com.slaviboy.liquidglass.backdrop.rememberLiquidGlassBackdrop
import com.slaviboy.liquidglass.glass.LiquidGlassBox
import com.slaviboy.liquidglass.glass.LocalLiquidGlassContentColor
import com.slaviboy.liquidglass.tabbar.LiquidGlassBadge
import com.slaviboy.liquidglass.tabbar.LiquidGlassTabBar
import com.slaviboy.liquidglass.tabbar.LiquidGlassTabBarDefaults
import com.slaviboy.liquidglass.tabbar.LiquidGlassTabItem
import com.slaviboy.liquidglass.tabbar.rememberLiquidGlassTabBarState

@Composable
fun DemoApp() {
    var config by remember { mutableStateOf(DemoConfig()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberLiquidGlassBackdrop()
    val barState = rememberLiquidGlassTabBarState()
    val log = remember { EventLogState() }
    val items = remember(config.tabCount, config.badges) { demoTabs(config.tabCount, config.badges) }

    LaunchedEffect(config.tabCount) {
        if (barState.selectedIndex >= config.tabCount) barState.select(config.tabCount - 1, animate = false)
    }

    val barPadding = LiquidGlassTabBarDefaults.contentBottomPadding()
    val bottomPadding = barPadding + if (config.showLog) 96.dp else 0.dp

    LiquidGlassTheme(settings = config.settings, style = config.style) {
        Box(Modifier.fillMaxSize()) {
            // Everything the glass refracts.
            Box(
                Modifier
                    .fillMaxSize()
                    .liquidGlassBackdrop(backdrop)
                    .background(MaterialTheme.colorScheme.background)
                    .nestedScroll(barState.nestedScrollConnection)
            ) {
                Crossfade(targetState = barState.selectedIndex, label = "screen") { index ->
                    DemoScreen(index, bottomPadding)
                }
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + slideInVertically { -it / 4 },
                    exit = fadeOut() + slideOutVertically { -it / 4 }
                ) {
                    Surface(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 72.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f),
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 12.dp
                    ) {
                        SettingsPanel(config, { config = it }, barState)
                    }
                }
            }

            // A glass button: LiquidGlassBox (Modifier.liquidGlass + adaptive content color).
            LiquidGlassBox(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 16.dp)
                    .size(52.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSettings = !showSettings },
                shape = CircleShape,
                interactive = true,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (showSettings) DemoIcons.Close else DemoIcons.Settings,
                    contentDescription = "Settings",
                    tint = LocalLiquidGlassContentColor.current
                )
            }

            if (config.showLog) {
                LiquidGlassBox(
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = barPadding, start = 21.dp, end = 21.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    EventLog(log, contentColor = LocalLiquidGlassContentColor.current)
                }
            }

            LiquidGlassTabBar(
                items = items,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                state = barState,
                minimizeBehavior = config.minimizeBehavior,
                onTabSelected = { log.add("onTabSelected($it)") },
                onTabReselected = { log.add("onTabReselected($it)") },
                onPressStart = { log.add("onPressStart($it)") },
                onDrag = { log.addDrag(it) },
                onRelease = { index, cancelled -> log.add("onRelease($index, cancelled=$cancelled)") },
                onMinimizedChange = { log.add("onMinimizedChange($it)") }
            )
        }
    }
}

private fun demoTabs(count: Int, badges: Boolean): List<LiquidGlassTabItem> = listOf(
    LiquidGlassTabItem("Home", DemoIcons.HomeOutlined, selectedIcon = DemoIcons.Home),
    LiquidGlassTabItem("Stats", DemoIcons.Stats, badge = if (badges) LiquidGlassBadge.Dot else null),
    LiquidGlassTabItem("Friends", DemoIcons.Friends, badge = if (badges) LiquidGlassBadge.Count(3) else null),
    LiquidGlassTabItem("Search", DemoIcons.Search),
    LiquidGlassTabItem("Favorites", DemoIcons.Favorite)
).take(count)
