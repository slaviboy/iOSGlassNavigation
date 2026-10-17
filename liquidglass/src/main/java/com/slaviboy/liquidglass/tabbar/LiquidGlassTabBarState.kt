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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.slaviboy.liquidglass.physics.TabBarEvent
import com.slaviboy.liquidglass.physics.TabBarPhysics
import kotlinx.coroutines.channels.Channel
import kotlin.math.sign

/** iOS `tabBarMinimizeBehavior`. */
enum class TabBarMinimizeBehavior {
    /** The bar never minimizes. */
    Never,

    /** Minimizes when the content scrolls down (toward its end), expands when scrolling up. */
    OnScrollDown,

    /** Minimizes when the content scrolls up (toward its start), expands when scrolling down. */
    OnScrollUp
}

/**
 * State of a [LiquidGlassTabBar]: the selected tab, the live lens position while dragging and the
 * minimized state. Animated values are observable; reading them in a draw or layout lambda only
 * invalidates that phase.
 */
@Stable
class LiquidGlassTabBarState(
    initialSelectedIndex: Int = 0,
    initialMinimized: Boolean = false
) {
    internal val physics = TabBarPhysics(initialSelectedIndex).also {
        if (initialMinimized) {
            it.setMinimized(true)
            it.minimize.snapTo(1f)
        }
    }

    /** Bumped once per animation frame; draw lambdas read it to follow the physics. */
    internal val frame = mutableLongStateOf(0L)
    private val selectedState = mutableIntStateOf(physics.selectedIndex)
    private val minimizedState = mutableStateOf(initialMinimized)
    internal val minimizeProgressState = mutableFloatStateOf(if (initialMinimized) 1f else 0f)

    /** The selected tab. */
    val selectedIndex: Int get() = selectedState.intValue

    /** Live lens/selection position as a fractional tab index (follows the finger while dragging). */
    val fractionalIndex: Float
        get() {
            frame.longValue
            return physics.fractionalIndex
        }

    /** 0 = resting frosted pill, 1 = fully lifted clear lens. */
    val lift: Float
        get() {
            frame.longValue
            return physics.lift.value
        }

    val isPressed: Boolean
        get() {
            frame.longValue
            return physics.isPressed
        }

    val isDragging: Boolean
        get() {
            frame.longValue
            return physics.isDragging
        }

    val isMinimized: Boolean get() = minimizedState.value

    /** 0 = expanded, 1 = minimized. */
    val minimizeProgress: Float get() = minimizeProgressState.floatValue

    /** Selects [index] programmatically (e.g. from a pager). Listeners are not called. */
    fun select(index: Int, animate: Boolean = true) {
        physics.select(index, animate)
        selectedState.intValue = physics.selectedIndex
        wake()
    }

    fun minimize() = setMinimized(true)

    fun expand() = setMinimized(false)

    /** Connect to scrollable content with `Modifier.nestedScroll` for minimize-on-scroll. */
    val nestedScrollConnection: NestedScrollConnection = MinimizeOnScrollConnection(this)

    internal var minimizeBehavior = TabBarMinimizeBehavior.Never
    internal var minimizeThresholdPx = 64f
    internal var eventSink: ((TabBarEvent) -> Unit)? = null

    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    internal fun setMinimized(value: Boolean) {
        if (minimizedState.value == value) return
        minimizedState.value = value
        physics.setMinimized(value)
        eventSink?.invoke(TabBarEvent.MinimizedChanged(value))
        wake()
    }

    internal fun wake() {
        wakeSignal.trySend(Unit)
    }

    internal fun onDown(x: Float, y: Float) {
        physics.onDown(x, y, System.nanoTime())
        drainEvents()
        wake()
    }

    internal fun onMove(x: Float, y: Float) {
        physics.onMove(x, y)
        wake()
    }

    internal fun onUp() {
        physics.onUp(System.nanoTime())
        drainEvents()
        wake()
    }

    internal fun onCancel() {
        physics.onCancel(System.nanoTime())
        drainEvents()
        wake()
    }

    internal fun activate(index: Int) {
        physics.activate(index)
        drainEvents()
        wake()
    }

    /** Runs the physics while anything moves, sleeping in between. Started by the tab bar. */
    internal suspend fun runLoop() {
        while (true) {
            wakeSignal.receive()
            var active = true
            while (active) {
                active = withFrameNanos { time ->
                    val running = physics.tick(time)
                    publish()
                    running
                }
            }
        }
    }

    private fun publish() {
        frame.longValue++
        val p = physics.minimize.value
        if (minimizeProgressState.floatValue != p) minimizeProgressState.floatValue = p
        drainEvents()
    }

    private fun drainEvents() {
        val events = physics.events
        if (events.isEmpty()) return
        val pending = events.toTypedArray()
        events.clear()
        for (event in pending) {
            when (event) {
                is TabBarEvent.Selected -> selectedState.intValue = event.index
                is TabBarEvent.ExpandRequest -> setMinimized(false)
                else -> Unit
            }
            eventSink?.invoke(event)
        }
    }

    companion object {
        val Saver: Saver<LiquidGlassTabBarState, *> = listSaver(
            save = { listOf(it.selectedIndex, if (it.isMinimized) 1 else 0) },
            restore = { LiquidGlassTabBarState(it[0], it[1] == 1) }
        )
    }
}

@Composable
fun rememberLiquidGlassTabBarState(
    initialSelectedIndex: Int = 0,
    initialMinimized: Boolean = false
): LiquidGlassTabBarState = rememberSaveable(saver = LiquidGlassTabBarState.Saver) {
    LiquidGlassTabBarState(initialSelectedIndex, initialMinimized)
}

/** Minimizes/expands the bar from the scroll direction of the connected content. */
private class MinimizeOnScrollConnection(private val state: LiquidGlassTabBarState) : NestedScrollConnection {
    private var accumulated = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val behavior = state.minimizeBehavior
        if (behavior == TabBarMinimizeBehavior.Never) return Offset.Zero
        val dy = consumed.y
        if (dy != 0f) {
            if (accumulated != 0f && sign(dy) != sign(accumulated)) accumulated = 0f
            accumulated += dy
            val minimizeOnDown = behavior == TabBarMinimizeBehavior.OnScrollDown
            if (accumulated <= -state.minimizeThresholdPx) {
                state.setMinimized(minimizeOnDown)
                accumulated = 0f
            } else if (accumulated >= state.minimizeThresholdPx) {
                state.setMinimized(!minimizeOnDown)
                accumulated = 0f
            }
        }
        // Pulling past the top expands the bar again.
        if (available.y > 0f && behavior == TabBarMinimizeBehavior.OnScrollDown && source == NestedScrollSource.UserInput) {
            state.setMinimized(false)
        }
        return Offset.Zero
    }
}
