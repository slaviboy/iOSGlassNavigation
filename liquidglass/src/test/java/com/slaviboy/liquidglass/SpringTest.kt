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

import com.slaviboy.liquidglass.physics.SpringSpec
import com.slaviboy.liquidglass.physics.SpringValue
import com.slaviboy.liquidglass.physics.TabBarSprings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringTest {

    @Test
    fun responseConvertsToStiffness() {
        assertEquals(631.65f, SpringSpec.fromResponse(0.25f, 1f).stiffness, 0.05f)
        assertEquals(576.0f, SpringSpec.fromResponse(0.2618f, 0.75f).stiffness, 0.5f)
        assertEquals(240.7f, SpringSpec.fromResponse(0.405f, 0.85f).stiffness, 0.1f)
        assertEquals(1205f, SpringSpec.fromResponse(0.181f, 0.865f).stiffness, 1f)
        assertEquals(157.9f, SpringSpec.fromResponse(0.5f, 1f).stiffness, 0.05f)
    }

    @Test
    fun coefficientsConvertToDampingRatio() {
        assertEquals(1f, SpringSpec.fromCoefficients(631.65f, 50.27f).dampingRatio, 0.001f)
        assertEquals(0.625f, SpringSpec.fromCoefficients(541.5f, 29.09f).dampingRatio, 0.001f)
        assertEquals(0.6f, SpringSpec.fromCoefficients(304.6f, 20.94f).dampingRatio, 0.001f)
        assertEquals(1f, SpringSpec.fromCoefficients(157.9f, 25.13f).dampingRatio, 0.001f)
    }

    @Test
    fun criticallyDampedNeverOvershoots() {
        val spring = SpringValue(0f, TabBarSprings.Lift)
        spring.target = 1f
        repeat(200) {
            spring.step(1f / 120f)
            assertTrue("value ${spring.value} overshot", spring.value <= 1f + 1e-6f)
        }
        assertTrue(spring.isAtRest)
    }

    @Test
    fun swellOvershootsAboutEightPercent() {
        val spring = SpringValue(0f, TabBarSprings.SwellRise)
        spring.target = 1f
        var max = 0f
        repeat(240) {
            spring.step(1f / 240f)
            max = maxOf(max, spring.value)
        }
        assertEquals(0.08f, max - 1f, 0.01f)
    }

    @Test
    fun resultDoesNotDependOnFrameRate() {
        val a = SpringValue(0f, TabBarSprings.LensDrag).apply { target = 3f }
        val b = SpringValue(0f, TabBarSprings.LensDrag).apply { target = 3f }
        a.step(0.032f)
        b.step(0.016f)
        b.step(0.016f)
        assertEquals(a.value, b.value, 1e-4f)
        assertEquals(a.velocity, b.velocity, 1e-2f)
    }

    @Test
    fun overdampedSpringSettlesWithoutOvershoot() {
        val spring = SpringValue(0f, TabBarSprings.FlexEase)
        spring.target = 1f
        repeat(400) {
            spring.step(1f / 120f)
            assertTrue(spring.value <= 1f + 1e-6f)
        }
        assertEquals(1f, spring.value, 1e-3f)
    }

    @Test
    fun keepsVelocityWhenRetargeted() {
        val spring = SpringValue(0f, TabBarSprings.LensDrag)
        spring.target = 2f
        repeat(5) { spring.step(1f / 60f) }
        val velocity = spring.velocity
        spring.animateTo(1f, TabBarSprings.LensCatchUp)
        assertEquals(velocity, spring.velocity, 0f)
    }
}
