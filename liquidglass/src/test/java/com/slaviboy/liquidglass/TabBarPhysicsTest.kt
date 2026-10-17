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

import com.slaviboy.liquidglass.physics.TabBarEvent
import com.slaviboy.liquidglass.physics.TabBarPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Scripted gesture timelines. Bar: 4 tabs, 400 px wide, 62 px tall, 4 px inset, density 1, so
 * every tab is 98 px wide and the tab centers are at x = 53, 151, 249 and 347.
 */
class TabBarPhysicsTest {

    private lateinit var physics: TabBarPhysics
    private var now = 0L

    private val centers = floatArrayOf(53f, 151f, 249f, 347f)

    @Before
    fun setUp() {
        physics = TabBarPhysics(initialSelectedIndex = 0)
        physics.setLayout(tabCount = 4, barWidth = 400f, barHeight = 62f, inset = 4f, density = 1f, rtl = false)
        physics.touchSlop = 8f
        now = 1_000_000_000L
    }

    private fun ms(value: Long) = value * 1_000_000L

    /** Advances time by [durationMs] in 16 ms frames. */
    private fun run(durationMs: Long) {
        val end = now + ms(durationMs)
        while (now < end) {
            now += ms(16)
            physics.tick(now)
        }
    }

    /** Ticks until the physics reports it is idle. */
    private fun settle(maxMs: Long = 5000) {
        val end = now + ms(maxMs)
        do {
            now += ms(16)
        } while (physics.tick(now) && now < end)
    }

    private fun events(): List<TabBarEvent> = physics.events.toList().also { physics.events.clear() }

    private fun tap(x: Float) {
        physics.onDown(x, 31f, now)
        run(60)
        physics.onUp(now)
    }

    @Test
    fun tapOnAnotherTabSelectsIt() {
        tap(centers[2])
        val events = events().filter { it !is TabBarEvent.Drag && it !is TabBarEvent.TabCrossed }
        assertEquals(
            listOf(TabBarEvent.PressStart(2), TabBarEvent.Release(2, false), TabBarEvent.Selected(2)),
            events
        )
        assertEquals(2, physics.selectedIndex)
        settle()
        assertEquals(2f, physics.lens.value, 1e-3f)
        assertEquals(0f, physics.lift.value, 1e-3f)
    }

    @Test
    fun tapOnSelectedTabReselects() {
        tap(centers[0])
        val events = events()
        assertTrue(TabBarEvent.Reselected(0) in events)
        assertFalse(events.any { it is TabBarEvent.Selected })
    }

    @Test
    fun liftStartsFifteenMillisecondsAfterTouchDown() {
        physics.onDown(centers[1], 31f, now)
        physics.tick(now + ms(10))
        assertEquals(0f, physics.lift.target, 0f)
        assertEquals(0f, physics.lift.value, 0f)
        physics.tick(now + ms(20))
        assertEquals(1f, physics.lift.target, 0f)
    }

    @Test
    fun lensHangsAfterQuickTap() {
        physics.onDown(centers[0], 31f, now)
        run(40)
        physics.onUp(now)
        run(150)
        assertEquals("the lens is still lifted during the hang time", 1f, physics.lift.target, 0f)
        run(700)
        assertEquals(0f, physics.lift.target, 0f)
    }

    @Test
    fun dragAcrossTabsTicksOncePerTab() {
        physics.onDown(centers[0], 31f, now)
        var x = centers[0]
        while (x < centers[3]) {
            x += 10f
            physics.onMove(x, 31f)
            now += ms(16)
            physics.tick(now)
        }
        run(400)
        physics.onUp(now)
        val crossed = events().filterIsInstance<TabBarEvent.TabCrossed>().map { it.index }
        assertEquals(listOf(1, 2, 3), crossed)
        assertEquals(3, physics.selectedIndex)
        assertTrue(physics.isDragging.not())
    }

    @Test
    fun lensFollowsFingerWhileDragging() {
        physics.onDown(centers[0], 31f, now)
        physics.onMove(centers[2], 31f)
        run(600)
        assertTrue(physics.isPressed)
        assertTrue(physics.isDragging)
        assertEquals(2f, physics.lens.value, 0.01f)
        assertEquals(1f, physics.lift.value, 0.01f)
        assertTrue("the bar swells while pressed", physics.swell.value > 0.9f)
    }

    @Test
    fun cancelNeverSelects() {
        physics.onDown(centers[0], 31f, now)
        physics.onMove(centers[3], 31f)
        run(200)
        physics.onCancel(now)
        val events = events()
        assertFalse(events.any { it is TabBarEvent.Selected })
        assertTrue(TabBarEvent.Release(0, true) in events)
        settle()
        assertEquals(0, physics.selectedIndex)
        assertEquals(0f, physics.lens.value, 1e-3f)
    }

    @Test
    fun reduceMotionNeverLifts() {
        physics.reduceMotion = true
        physics.onDown(centers[0], 31f, now)
        physics.onMove(centers[3], 31f)
        repeat(60) {
            now += ms(16)
            physics.tick(now)
            assertEquals(0f, physics.lift.value, 0f)
            assertEquals(0f, physics.swell.value, 0f)
            assertEquals(0f, physics.flexEased.value, 0f)
        }
        assertEquals("the pill still slides under the finger", 3f, physics.lens.value, 0.05f)
        physics.onUp(now)
        assertEquals(3, physics.selectedIndex)
    }

    @Test
    fun fastDragFlexesTheLens() {
        physics.onDown(centers[0], 31f, now)
        run(30)
        physics.onMove(centers[3], 31f)
        var maxFlex = 0f
        repeat(20) {
            now += ms(16)
            physics.tick(now)
            maxFlex = maxOf(maxFlex, physics.flexEased.value)
        }
        assertTrue("accelerating stretches the droplet ($maxFlex)", maxFlex > 0.02f)
        assertTrue(maxFlex <= 0.46f)
    }

    @Test
    fun rightToLeftMapsSlotsToIndices() {
        physics.setLayout(tabCount = 4, barWidth = 400f, barHeight = 62f, inset = 4f, density = 1f, rtl = true)
        assertEquals(3f, physics.lens.value, 0f)
        tap(centers[0])
        assertTrue(TabBarEvent.Selected(3) in events())
        assertEquals(3, physics.selectedIndex)
    }

    @Test
    fun programmaticSelectEmitsNoEvents() {
        physics.select(2, animate = true)
        settle()
        assertTrue(events().none { it is TabBarEvent.Selected || it is TabBarEvent.PressStart })
        assertEquals(2, physics.selectedIndex)
        assertEquals(2f, physics.lens.value, 1e-3f)
        assertEquals(0f, physics.lift.value, 0f)
    }

    @Test
    fun accessibilityActivationSelects() {
        physics.activate(1)
        assertEquals(listOf<TabBarEvent>(TabBarEvent.Selected(1)), events())
        physics.activate(1)
        assertEquals(listOf<TabBarEvent>(TabBarEvent.Reselected(1)), events())
    }

    @Test
    fun pressOnMinimizedBarRequestsExpand() {
        physics.setMinimized(true)
        settle()
        assertEquals(1f, physics.minimize.value, 1e-3f)
        physics.onDown(20f, 20f, now)
        run(40)
        physics.onUp(now)
        val events = events()
        assertTrue(TabBarEvent.ExpandRequest in events)
        assertFalse(events.any { it is TabBarEvent.Selected || it is TabBarEvent.PressStart })
    }

    @Test
    fun goesIdleOnceSettled() {
        tap(centers[1])
        settle()
        assertFalse(physics.tick(now + ms(16)))
    }

    @Test
    fun clampsSelectionWhenTabsAreRemoved() {
        physics.select(3, animate = false)
        physics.setLayout(tabCount = 2, barWidth = 400f, barHeight = 62f, inset = 4f, density = 1f, rtl = false)
        assertEquals(1, physics.selectedIndex)
        assertEquals(1f, physics.lens.value, 0f)
    }
}
