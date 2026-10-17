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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slaviboy.liquidglass.LocalLiquidGlassSettings
import com.slaviboy.liquidglass.LocalLiquidGlassStyle
import com.slaviboy.liquidglass.backdrop.LiquidGlassBackdrop
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.config.GlassFace
import com.slaviboy.liquidglass.config.LiquidGlassSettings
import com.slaviboy.liquidglass.config.LiquidGlassStyle
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.config.resolveGlass
import com.slaviboy.liquidglass.currentGlassEnvironment
import com.slaviboy.liquidglass.effect.GlassPrograms
import com.slaviboy.liquidglass.glass.GlassGeometry.lerp
import com.slaviboy.liquidglass.physics.TabBarEvent
import com.slaviboy.liquidglass.system.BackdropLumaSampler
import com.slaviboy.liquidglass.system.GlassHaptics
import com.slaviboy.liquidglass.system.rememberAdaptiveGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The iOS 26 Liquid Glass tab bar.
 *
 * Place it as a later sibling of the content marked with
 * [com.slaviboy.liquidglass.backdrop.liquidGlassBackdrop] (typically aligned to the bottom of a
 * Box that fills the screen). Press anywhere on the bar and the selection lifts into a clear lens
 * that follows the finger; release to select the tab under it.
 *
 * @param items the tabs.
 * @param backdrop the content the glass refracts.
 * @param state selection, live lens position and minimized state.
 * @param style optics and colors of the glass (see [LiquidGlassStyle]).
 * @param settings the iOS settings: variant, Clear/Tinted, accessibility, tilt, haptics, tier.
 * @param minimizeBehavior connect [LiquidGlassTabBarState.nestedScrollConnection] to scrolling
 *   content to use it.
 * @param onTabSelected the user selected a different tab (tap or drag release).
 * @param onTabReselected the user tapped the already selected tab.
 * @param onPressStart a finger went down on the tab with this index.
 * @param onDrag the lens moved; fractional tab index under the lens.
 * @param onRelease the finger was lifted (or the gesture cancelled) over this tab.
 * @param onMinimizedChange the bar minimized or expanded.
 */
@Composable
fun LiquidGlassTabBar(
    items: List<LiquidGlassTabItem>,
    backdrop: LiquidGlassBackdrop,
    modifier: Modifier = Modifier,
    state: LiquidGlassTabBarState = rememberLiquidGlassTabBarState(),
    style: LiquidGlassStyle = LocalLiquidGlassStyle.current,
    settings: LiquidGlassSettings = LocalLiquidGlassSettings.current,
    colors: LiquidGlassTabBarColors = LiquidGlassTabBarDefaults.colors(),
    dimensions: LiquidGlassTabBarDimensions = LiquidGlassTabBarDefaults.dimensions(),
    windowInsets: WindowInsets = LiquidGlassTabBarDefaults.windowInsets,
    minimizeBehavior: TabBarMinimizeBehavior = TabBarMinimizeBehavior.Never,
    onTabSelected: (index: Int) -> Unit = {},
    onTabReselected: (index: Int) -> Unit = {},
    onPressStart: (index: Int) -> Unit = {},
    onDrag: (fractionalIndex: Float) -> Unit = {},
    onRelease: (index: Int, cancelled: Boolean) -> Unit = { _, _ -> },
    onMinimizedChange: (minimized: Boolean) -> Unit = {}
) {
    LiquidGlassTabBar(
        tabCount = items.size,
        backdrop = backdrop,
        modifier = modifier,
        state = state,
        style = style,
        settings = settings,
        colors = colors,
        dimensions = dimensions,
        windowInsets = windowInsets,
        minimizeBehavior = minimizeBehavior,
        onTabSelected = onTabSelected,
        onTabReselected = onTabReselected,
        onPressStart = onPressStart,
        onDrag = onDrag,
        onRelease = onRelease,
        onMinimizedChange = onMinimizedChange,
        tabContentDescription = { items[it].contentDescription }
    ) { index ->
        DefaultTabContent(items[index], dimensions, colors)
    }
}

/** Slot overload of [LiquidGlassTabBar]: [tab] draws the content of each tab. */
@Composable
fun LiquidGlassTabBar(
    tabCount: Int,
    backdrop: LiquidGlassBackdrop,
    modifier: Modifier = Modifier,
    state: LiquidGlassTabBarState = rememberLiquidGlassTabBarState(),
    style: LiquidGlassStyle = LocalLiquidGlassStyle.current,
    settings: LiquidGlassSettings = LocalLiquidGlassSettings.current,
    colors: LiquidGlassTabBarColors = LiquidGlassTabBarDefaults.colors(),
    dimensions: LiquidGlassTabBarDimensions = LiquidGlassTabBarDefaults.dimensions(),
    windowInsets: WindowInsets = LiquidGlassTabBarDefaults.windowInsets,
    minimizeBehavior: TabBarMinimizeBehavior = TabBarMinimizeBehavior.Never,
    onTabSelected: (index: Int) -> Unit = {},
    onTabReselected: (index: Int) -> Unit = {},
    onPressStart: (index: Int) -> Unit = {},
    onDrag: (fractionalIndex: Float) -> Unit = {},
    onRelease: (index: Int, cancelled: Boolean) -> Unit = { _, _ -> },
    onMinimizedChange: (minimized: Boolean) -> Unit = {},
    tabContentDescription: (index: Int) -> String? = { null },
    tab: @Composable LiquidGlassTabScope.(index: Int) -> Unit
) {
    val environment = currentGlassEnvironment(settings)
    val system by environment.system
    val resolved = remember(settings, style, system, environment.deviceMaxTier) {
        resolveGlass(settings, style, system, environment.deviceMaxTier)
    }
    val density = LocalDensity.current
    val systemDark = isSystemInDarkTheme()

    val graphicsContext = LocalGraphicsContext.current
    val renderer = remember(graphicsContext) { TabBarRenderer(graphicsContext) }
    val sampler = remember(graphicsContext) { BackdropLumaSampler(graphicsContext) }
    DisposableEffect(renderer, sampler) {
        onDispose {
            renderer.release()
            sampler.release()
        }
    }

    // AGSL compiles off the main thread; until then the Blur tier is drawn.
    val programsReady = remember(renderer) { mutableStateOf(renderer.programs != null) }
    LaunchedEffect(renderer, resolved.tier) {
        if (resolved.tier == RenderTier.Full && renderer.programs == null) {
            val compiled = withContext(Dispatchers.Default) { GlassPrograms.compile(withLens = true) }
            renderer.programs = compiled
            programsReady.value = compiled != null
        }
    }

    val adaptive = rememberAdaptiveGlass(settings.colorScheme, resolved.adaptive, systemDark, resolved.clearVariant) {
        sampler.sample(backdrop, renderer.coordinates, density)
    }
    val darkGlass = adaptive.isDark
    val contentColors = remember(colors, resolved, darkGlass) {
        arrayOf(Color.Unspecified, Color.Unspecified).also { resolveContentColors(colors, resolved, darkGlass, it) }
    }

    // Listeners and haptics.
    val haptic = LocalHapticFeedback.current
    val haptics = remember { GlassHaptics() }
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnTabReselected by rememberUpdatedState(onTabReselected)
    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnMinimizedChange by rememberUpdatedState(onMinimizedChange)
    DisposableEffect(state) {
        state.eventSink = { event ->
            when (event) {
                is TabBarEvent.PressStart -> currentOnPressStart(event.index)
                is TabBarEvent.TabCrossed -> haptics.tick()
                is TabBarEvent.Drag -> currentOnDrag(event.fractionalIndex)
                is TabBarEvent.Release -> currentOnRelease(event.index, event.cancelled)
                is TabBarEvent.Selected -> {
                    haptics.confirm()
                    currentOnTabSelected(event.index)
                }
                is TabBarEvent.Reselected -> currentOnTabReselected(event.index)
                is TabBarEvent.MinimizedChanged -> currentOnMinimizedChange(event.minimized)
                TabBarEvent.ExpandRequest -> Unit
            }
        }
        onDispose { state.eventSink = null }
    }
    SideEffect {
        haptics.feedback = haptic
        haptics.enabled = resolved.haptics
        state.physics.reduceMotion = resolved.reduceMotion
        state.minimizeBehavior = minimizeBehavior
        state.minimizeThresholdPx = with(density) { 24.dp.toPx() }
    }
    LaunchedEffect(state) { state.runLoop() }
    LaunchedEffect(state, resolved.reduceMotion) { state.wake() }

    val face = remember { GlassFace() }
    val drawInput = remember { TabBarDrawInput() }
    val metrics = remember(state) { BarMetrics() }
    val positionTick = remember { mutableIntStateOf(0) }
    val lastPosition = remember { mutableStateOf(Offset.Unspecified) }
    val tilt = environment.tilt
    val bottomOffset = LiquidGlassTabBarDefaults.bottomOffset(dimensions, windowInsets)

    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = dimensions.horizontalMargin,
                end = dimensions.horizontalMargin,
                bottom = bottomOffset
            )
    ) {
        TabBarLayout(
            state = state,
            tabCount = tabCount,
            dimensions = dimensions,
            metrics = metrics,
            modifier = Modifier
                // Own layer: per-frame redraws of the bar stay local and never re-record the backdrop,
                // which would make every glass element re-blur its content. No clip: the lens and the
                // shadows draw outside the bar.
                .graphicsLayer()
                .onGloballyPositioned {
                    renderer.coordinates = it
                    val p = it.positionInRoot()
                    if (p != lastPosition.value) {
                        lastPosition.value = p
                        positionTick.intValue++
                    }
                }
                .drawWithContent {
                    state.frame.longValue
                    positionTick.intValue
                    backdrop.version.intValue
                    programsReady.value
                    face.update(resolved, adaptive.luma.value, darkGlass, systemDark)
                    drawInput.physics = state.physics
                    drawInput.resolved = resolved
                    drawInput.face = face
                    drawInput.backdrop = backdrop
                    drawInput.darkGlass = darkGlass
                    drawInput.lightAngleDeg = resolved.specularAngle + if (resolved.deviceTilt) tilt.value else 0f
                    drawInput.minimizeProgress = state.minimizeProgressState.floatValue
                    drawInput.fullWidth = metrics.fullWidth
                    drawInput.baseHeight = metrics.baseHeight
                    drawInput.inset = dimensions.inset.toPx()
                    drawInput.lensGrow = dimensions.lensGrow.toPx()
                    drawInput.selectionSize = GlassConstants.MinimizedSelection.dp.toPx()
                    drawInput.rowOffsetX = metrics.rowOffsetX
                    renderer.draw(this, drawInput)
                }
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        state.physics.touchSlop = viewConfiguration.touchSlop
                        state.onDown(down.position.x - metrics.rowOffsetX, down.position.y)
                        val id = down.id
                        var finished = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id }
                                if (change == null || change.isConsumed) {
                                    state.onCancel()
                                    finished = true
                                    break
                                }
                                if (!change.pressed) {
                                    change.consume()
                                    state.onUp()
                                    finished = true
                                    break
                                }
                                state.onMove(change.position.x - metrics.rowOffsetX, change.position.y)
                                if (state.physics.isDragging) change.consume()
                            }
                        } finally {
                            if (!finished) state.onCancel()
                        }
                    }
                }
                .selectableGroup()
        ) {
            TabRow(
                state = state,
                tabCount = tabCount,
                dimensions = dimensions,
                metrics = metrics,
                selectedColor = contentColors[0],
                unselectedColor = contentColors[1],
                lensCopy = false,
                tabContentDescription = tabContentDescription,
                modifier = Modifier.graphicsLayer {
                    state.frame.longValue
                    val ph = state.physics
                    val swell = 1f + GlassConstants.Swell * ph.swell.value
                    val e = abs(ph.stretchX.value) - abs(ph.stretchY.value)
                    scaleX = swell * (1f + e)
                    scaleY = swell / (1f + e)
                    translationX = GlassConstants.StretchDrift * ph.stretchX.value * metrics.width * 0.5f
                    translationY = GlassConstants.StretchDrift * ph.stretchY.value * metrics.height * 0.5f
                    val pivotX = (metrics.width / 2f - metrics.rowOffsetX) / metrics.fullWidth.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(pivotX, 0.5f)
                },
                tab = tab
            )
            // Accent copy of the row: recorded but never drawn; only visible through the lens.
            TabRow(
                state = state,
                tabCount = tabCount,
                dimensions = dimensions,
                metrics = metrics,
                selectedColor = contentColors[0],
                unselectedColor = contentColors[0],
                lensCopy = true,
                tabContentDescription = tabContentDescription,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        renderer.accent.record { this@drawWithContent.drawContent() }
                    },
                tab = tab
            )
        }
    }
}

/** Sizes resolved during layout, read by drawing, gestures and item transforms. */
internal class BarMetrics {
    var fullWidth = 1f
    var baseHeight = 1f
    var width = 1f
    var height = 1f
    var rowOffsetX = 0f
    var labelShift = 0f
}

/** Measures the rows at full width; the bar itself shrinks to a circle when minimized. */
@Composable
private fun TabBarLayout(
    state: LiquidGlassTabBarState,
    tabCount: Int,
    dimensions: LiquidGlassTabBarDimensions,
    metrics: BarMetrics,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val full = if (constraints.hasBoundedWidth) constraints.maxWidth else 360.dp.roundToPx()
        val base = dimensions.height.roundToPx()
        val minSize = dimensions.minimizedSize.roundToPx()
        val p = state.minimizeProgressState.floatValue.coerceIn(0f, 1.2f)
        val w = lerp(full.toFloat(), minSize.toFloat(), p).roundToInt().coerceIn(minSize / 2, full)
        val h = lerp(base.toFloat(), minSize.toFloat(), p).roundToInt().coerceAtLeast(1)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val rowX = if (rtl) w - full else 0
        metrics.fullWidth = full.toFloat()
        metrics.baseHeight = base.toFloat()
        metrics.width = w.toFloat()
        metrics.height = h.toFloat()
        metrics.rowOffsetX = rowX.toFloat()
        metrics.labelShift = (dimensions.labelSize.toPx() * 1.25f + 2.dp.toPx()) / 2f
        state.physics.setLayout(tabCount, full.toFloat(), base.toFloat(), dimensions.inset.toPx(), density, rtl)
        val childConstraints = Constraints.fixed(full, base)
        val placeables = measurables.map { it.measure(childConstraints) }
        layout(w, h) {
            placeables.forEach { it.place(rowX, (h - base) / 2) }
        }
    }
}

@Composable
private fun TabRow(
    state: LiquidGlassTabBarState,
    tabCount: Int,
    dimensions: LiquidGlassTabBarDimensions,
    metrics: BarMetrics,
    selectedColor: Color,
    unselectedColor: Color,
    lensCopy: Boolean,
    tabContentDescription: (Int) -> String?,
    modifier: Modifier,
    tab: @Composable LiquidGlassTabScope.(index: Int) -> Unit
) {
    val selected = state.selectedIndex
    Row(modifier.fillMaxSize().padding(horizontal = dimensions.inset)) {
        for (i in 0 until tabCount) {
            key(i) {
                val isSelected = lensCopy || i == selected
                val color = if (isSelected) selectedColor else unselectedColor
                val scope = remember(i, isSelected, color, lensCopy, state) {
                    TabScope(i, isSelected, color, lensCopy, state)
                }
                val itemSelected = i == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val p = state.minimizeProgressState.floatValue
                            if (p <= 0f) {
                                alpha = 1f
                                translationX = 0f
                                translationY = 0f
                                return@graphicsLayer
                            }
                            if (itemSelected) {
                                val ph = state.physics
                                val slot = ph.toSlot(i)
                                val slotCenter = ph.inset + ph.itemWidth * (slot + 0.5f)
                                val circleCenter = metrics.width / 2f - metrics.rowOffsetX
                                translationX = (circleCenter - slotCenter) * p
                                translationY = metrics.labelShift * p
                                alpha = 1f
                            } else {
                                alpha = (1f - 3f * p).coerceIn(0f, 1f)
                                translationX = 0f
                                translationY = 0f
                            }
                        }
                        .then(
                            if (lensCopy) {
                                Modifier
                            } else {
                                Modifier.semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = itemSelected
                                    tabContentDescription(i)?.let { contentDescription = it }
                                    onClick {
                                        state.activate(i)
                                        true
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    scope.tab(i)
                }
            }
        }
    }
}

@Stable
private class TabScope(
    override val index: Int,
    override val isSelected: Boolean,
    override val contentColor: Color,
    override val isLensCopy: Boolean,
    private val state: LiquidGlassTabBarState
) : LiquidGlassTabScope {
    override val minimizeProgress: Float get() = state.minimizeProgress
}

/** Icon over label, as on iOS, with an optional badge. */
@Composable
private fun LiquidGlassTabScope.DefaultTabContent(
    item: LiquidGlassTabItem,
    dimensions: LiquidGlassTabBarDimensions,
    colors: LiquidGlassTabBarColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Image(
                painter = rememberVectorPainter(if (isSelected) item.selectedIcon else item.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(dimensions.iconSize)
            )
            item.badge?.let { TabBadge(it, colors, Modifier.align(Alignment.TopEnd)) }
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = item.label,
            maxLines = 1,
            style = TextStyle(
                color = contentColor,
                fontSize = dimensions.labelSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.graphicsLayer { alpha = (1f - 2f * minimizeProgress).coerceIn(0f, 1f) }
        )
    }
}

@Composable
private fun TabBadge(badge: LiquidGlassBadge, colors: LiquidGlassTabBarColors, modifier: Modifier) {
    when (badge) {
        LiquidGlassBadge.Dot -> Box(
            modifier
                .offset(x = 3.dp, y = (-2).dp)
                .size(9.dp)
                .background(colors.badgeColor, CircleShape)
        )

        is LiquidGlassBadge.Count, is LiquidGlassBadge.Text -> {
            val text = when (badge) {
                is LiquidGlassBadge.Count -> if (badge.count > badge.max) "${badge.max}+" else badge.count.toString()
                is LiquidGlassBadge.Text -> badge.text
                else -> ""
            }
            Box(
                modifier
                    .offset(x = 10.dp, y = (-5).dp)
                    .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                    .background(colors.badgeColor, RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = text,
                    maxLines = 1,
                    style = TextStyle(
                        color = colors.badgeContentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}
