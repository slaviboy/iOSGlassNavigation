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

package com.slaviboy.liquidglass.physics

import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.glass.GlassGeometry
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** Something the tab bar reports to listeners and haptics. Indices are logical (not RTL-mirrored). */
internal sealed interface TabBarEvent {
    data class PressStart(val index: Int) : TabBarEvent
    data class TabCrossed(val index: Int) : TabBarEvent
    data class Drag(val fractionalIndex: Float) : TabBarEvent
    data class Release(val index: Int, val cancelled: Boolean) : TabBarEvent
    data class Selected(val index: Int) : TabBarEvent
    data class Reselected(val index: Int) : TabBarEvent
    data class MinimizedChanged(val minimized: Boolean) : TabBarEvent
    data object ExpandRequest : TabBarEvent
}

/**
 * Pure gesture + spring engine of the iOS 26 tab bar. It knows nothing about Compose: inputs are
 * pointer positions in bar pixels and timestamps in nanoseconds, outputs are plain floats and a
 * queue of [events]. The lens position is kept in visual slot units (left to right).
 */
internal class TabBarPhysics(initialSelectedIndex: Int) {

    var tabCount = 1
        private set
    var barWidth = 0f
        private set
    var barHeight = 0f
        private set
    var inset = 0f
        private set
    var density = 1f
        private set
    var rtl = false
        private set
    var touchSlop = 8f

    var reduceMotion = false
        set(value) {
            field = value
            if (value) {
                lift.snapTo(0f)
                swell.snapTo(0f)
                stretchX.snapTo(0f)
                stretchY.snapTo(0f)
                flex.snapTo(0f)
                flexEased.snapTo(0f)
                glowScale.snapTo(1f)
                liftAt = NONE
            }
        }

    var minimized = false
        private set

    var selectedIndex = max(initialSelectedIndex, 0)
        private set

    val lens = SpringValue(selectedIndex.toFloat(), TabBarSprings.LensCatchUp)
    val lift = SpringValue(0f, TabBarSprings.Lift)
    val press = SpringValue(0f, TabBarSprings.PressRise)
    val swell = SpringValue(0f, TabBarSprings.SwellRise)
    val stretchX = SpringValue(0f, TabBarSprings.SwellRise, 1e-6f, 1e-5f)
    val stretchY = SpringValue(0f, TabBarSprings.SwellRise, 1e-6f, 1e-5f)
    val flex = SpringValue(0f, TabBarSprings.FlexReturn)
    val flexEased = SpringValue(0f, TabBarSprings.FlexEase)
    val glowScale = SpringValue(1f, TabBarSprings.PressRise)
    val minimize = SpringValue(0f, TabBarSprings.Minimize)

    var fingerX = 0f
        private set
    var fingerY = 0f
        private set
    var isPressed = false
        private set
    var isDragging = false
        private set

    /** +1 when the lens last moved right, -1 when it moved left. */
    var flexDirection = 1f
        private set

    val events = ArrayList<TabBarEvent>()

    private var pressedWhileMinimized = false
    private var downX = 0f
    private var downY = 0f
    private var startSlot = 0
    private var crossedSlot = 0
    private var releaseSlot = selectedIndex
    private var liftAt = NONE
    private var catchUpAt = NONE
    private var liftHoldUntil = NONE
    private var liftDeadline = NONE
    private var lastTick = NONE
    private var smoothedVelocity = 0f
    private var lastDragValue = Float.NaN

    val itemWidth: Float
        get() = max(1f, (barWidth - 2f * inset) / tabCount)

    /** Lens position as a logical fractional index. */
    val fractionalIndex: Float
        get() = if (rtl) tabCount - 1 - lens.value else lens.value

    fun setLayout(tabCount: Int, barWidth: Float, barHeight: Float, inset: Float, density: Float, rtl: Boolean) {
        val count = max(tabCount, 1)
        val structureChanged = count != this.tabCount || rtl != this.rtl
        this.tabCount = count
        this.barWidth = barWidth
        this.barHeight = barHeight
        this.inset = inset
        this.density = density
        this.rtl = rtl
        if (structureChanged) {
            selectedIndex = selectedIndex.coerceIn(0, count - 1)
            releaseSlot = toSlot(selectedIndex)
            if (!isPressed) lens.snapTo(releaseSlot.toFloat())
        }
    }

    /** Logical index <-> visual slot (the mapping is its own inverse). */
    fun toSlot(index: Int): Int = if (rtl) tabCount - 1 - index else index

    fun slotAt(x: Float): Int = floor((x - inset) / itemWidth).toInt().coerceIn(0, tabCount - 1)

    private fun fingerSlot(x: Float): Float =
        GlassGeometry.rubberBand((x - inset) / itemWidth - 0.5f, 0f, (tabCount - 1).toFloat(), 0.3f)

    fun onDown(x: Float, y: Float, timeNanos: Long) {
        fingerX = x
        fingerY = y
        downX = x
        downY = y
        isPressed = true
        isDragging = false
        press.animateTo(1f, TabBarSprings.PressRise)
        if (!reduceMotion) swell.animateTo(1f, TabBarSprings.SwellRise)
        glowScale.snapTo(1f)
        lastDragValue = Float.NaN
        if (minimized) {
            pressedWhileMinimized = true
            return
        }
        pressedWhileMinimized = false
        startSlot = slotAt(x)
        crossedSlot = startSlot
        liftAt = if (reduceMotion) NONE else timeNanos + seconds(GlassConstants.LiftDelay)
        catchUpAt = NONE
        liftHoldUntil = NONE
        liftDeadline = NONE
        lens.animateTo(fingerSlot(x), TabBarSprings.LensDrag)
        events += TabBarEvent.PressStart(toSlot(startSlot))
    }

    fun onMove(x: Float, y: Float) {
        if (!isPressed) return
        fingerX = x
        fingerY = y
        if (!isDragging && (abs(x - downX) > touchSlop || abs(y - downY) > touchSlop)) {
            isDragging = true
        }
        if (pressedWhileMinimized) return
        lens.animateTo(fingerSlot(x), TabBarSprings.LensDrag)
        if (!reduceMotion) updateStretch()
    }

    fun onUp(timeNanos: Long) = release(timeNanos, cancelled = false)

    fun onCancel(timeNanos: Long) = release(timeNanos, cancelled = true)

    private fun updateStretch() {
        val travel = abs(fingerX - downX) / density
        val outsideX = (max(0f, -fingerX) + max(0f, fingerX - barWidth)) / density
        val outsideY = (max(0f, -fingerY) + max(0f, fingerY - barHeight)) / density
        val a = min(
            GlassConstants.StretchPerTravel * travel + GlassConstants.StretchPerOutside * outsideX,
            GlassConstants.StretchMax
        )
        val b = min(GlassConstants.StretchPerOutside * outsideY, GlassConstants.StretchMax)
        val dirX = if (fingerX >= barWidth / 2f) 1f else -1f
        val dirY = if (fingerY >= barHeight / 2f) 1f else -1f
        stretchX.animateTo(a * dirX, TabBarSprings.SwellRise)
        stretchY.animateTo(b * dirY, TabBarSprings.SwellRise)
    }

    private fun release(timeNanos: Long, cancelled: Boolean) {
        if (!isPressed) return
        isPressed = false
        val wasDragging = isDragging
        isDragging = false
        press.animateTo(0f, TabBarSprings.PressFall)
        swell.animateTo(0f, TabBarSprings.SwellFall)
        stretchX.animateTo(0f, TabBarSprings.SwellFall)
        stretchY.animateTo(0f, TabBarSprings.SwellFall)
        glowScale.animateTo(if (reduceMotion) 1f else 4f, TabBarSprings.PressFall)
        if (pressedWhileMinimized) {
            pressedWhileMinimized = false
            if (!cancelled) events += TabBarEvent.ExpandRequest
            return
        }
        val targetSlot = if (cancelled) toSlot(selectedIndex) else slotAt(fingerX)
        releaseSlot = targetSlot
        catchUpAt = timeNanos + seconds(GlassConstants.CatchUpDelay)
        liftHoldUntil = timeNanos + if (wasDragging) 0L else seconds(GlassConstants.LensHangTime)
        liftDeadline = timeNanos + seconds(GlassConstants.LiftTimeout)
        val index = toSlot(targetSlot)
        events += TabBarEvent.Release(index, cancelled)
        if (!cancelled) {
            if (index != selectedIndex) {
                selectedIndex = index
                events += TabBarEvent.Selected(index)
            } else if (!wasDragging) {
                events += TabBarEvent.Reselected(index)
            }
        }
    }

    /** Programmatic selection: the pill slides without lifting and no listener is called. */
    fun select(index: Int, animate: Boolean) {
        selectedIndex = index.coerceIn(0, tabCount - 1)
        if (isPressed) return
        releaseSlot = toSlot(selectedIndex)
        catchUpAt = NONE
        if (animate) {
            lens.animateTo(releaseSlot.toFloat(), TabBarSprings.LensCatchUp)
        } else {
            lens.snapTo(releaseSlot.toFloat())
        }
    }

    /** Selection by accessibility services: behaves like a tap, without the lens lift. */
    fun activate(index: Int) {
        val i = index.coerceIn(0, tabCount - 1)
        if (i == selectedIndex) {
            events += TabBarEvent.Reselected(i)
        } else {
            select(i, animate = true)
            events += TabBarEvent.Selected(i)
        }
    }

    fun setMinimized(value: Boolean) {
        minimized = value
        minimize.animateTo(
            if (value) 1f else 0f,
            if (reduceMotion) TabBarSprings.MinimizeReduced else TabBarSprings.Minimize
        )
    }

    /** Advances every spring to [frameNanos]. Returns whether another frame is needed. */
    fun tick(frameNanos: Long): Boolean {
        val dt = if (lastTick == NONE) 1f / 60f else ((frameNanos - lastTick) / 1e9f).coerceIn(0f, 1f / 30f)
        lastTick = frameNanos

        if (liftAt != NONE && frameNanos >= liftAt) {
            liftAt = NONE
            if (!reduceMotion) lift.animateTo(1f, TabBarSprings.Lift)
        }
        if (catchUpAt != NONE && frameNanos >= catchUpAt) {
            catchUpAt = NONE
            lens.animateTo(releaseSlot.toFloat(), TabBarSprings.LensCatchUp)
        }
        if (!isPressed && lift.target > 0f && liftAt == NONE && catchUpAt == NONE) {
            val settled = abs(lens.value - releaseSlot) < 0.025f
            if ((frameNanos >= liftHoldUntil && settled) || frameNanos >= liftDeadline) {
                lift.animateTo(0f, TabBarSprings.Lift)
            }
        }

        lens.step(dt)
        lift.step(dt)
        press.step(dt)
        swell.step(dt)
        stretchX.step(dt)
        stretchY.step(dt)
        glowScale.step(dt)
        minimize.step(dt)
        if (reduceMotion) {
            flex.snapTo(0f)
            flexEased.snapTo(0f)
            smoothedVelocity = 0f
        } else {
            stepFlex(dt)
        }

        if (isPressed && !pressedWhileMinimized) {
            val slot = lens.value.roundToInt().coerceIn(0, tabCount - 1)
            if (slot != crossedSlot) {
                crossedSlot = slot
                events += TabBarEvent.TabCrossed(toSlot(slot))
                if (slot != startSlot && !reduceMotion) glowScale.animateTo(2f, TabBarSprings.PressRise)
            }
        }
        if ((isPressed && !pressedWhileMinimized) || !lens.isAtRest) {
            val f = fractionalIndex
            if (lastDragValue.isNaN() || abs(f - lastDragValue) > 0.001f) {
                lastDragValue = f
                events += TabBarEvent.Drag(f)
            }
        }

        val active = !lens.isAtRest || !lift.isAtRest || !press.isAtRest || !swell.isAtRest ||
            !stretchX.isAtRest || !stretchY.isAtRest || !glowScale.isAtRest || !minimize.isAtRest ||
            !flex.isAtRest || !flexEased.isAtRest || liftAt != NONE || catchUpAt != NONE ||
            (!isPressed && lift.target > 0f)
        if (!active) lastTick = NONE
        return active
    }

    private fun stepFlex(dt: Float) {
        val velocityDp = lens.velocity * itemWidth / density
        val previous = smoothedVelocity
        smoothedVelocity += (velocityDp - smoothedVelocity) * (1f - exp(-dt / GlassConstants.FlexSmoothing))
        if (abs(smoothedVelocity) > 1f) flexDirection = sign(smoothedVelocity)
        // Speeding up along the motion kicks the droplet longer and flatter; braking makes it bulge.
        val kick = (smoothedVelocity - previous) * flexDirection * GlassConstants.FlexGain
        if (kick != 0f) {
            flex.value = (flex.value + kick).coerceIn(-GlassConstants.FlexLimit, GlassConstants.FlexLimit)
        }
        flex.target = 0f
        flex.step(dt)
        flexEased.target = flex.value
        flexEased.step(dt)
    }

    companion object {
        const val NONE = Long.MIN_VALUE

        fun seconds(s: Float): Long = (s * 1e9f).toLong()
    }
}
