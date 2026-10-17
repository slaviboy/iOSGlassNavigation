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

import com.slaviboy.liquidglass.glass.GlassGeometry
import com.slaviboy.liquidglass.glass.GlassMaterial
import com.slaviboy.liquidglass.config.GlassConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class GlassMathTest {

    // ---- Geometry ----

    @Test
    fun roundedRectDistance() {
        // Capsule 200 x 60.
        assertEquals(-30f, GlassGeometry.sdRoundRect(0f, 0f, 100f, 30f, 30f), 1e-4f)
        assertEquals(0f, GlassGeometry.sdRoundRect(100f, 0f, 100f, 30f, 30f), 1e-4f)
        assertEquals(0f, GlassGeometry.sdRoundRect(0f, -30f, 100f, 30f, 30f), 1e-4f)
        assertEquals(10f, GlassGeometry.sdRoundRect(110f, 0f, 100f, 30f, 30f), 1e-4f)
        assertEquals(-5f, GlassGeometry.sdRoundRect(20f, 25f, 100f, 30f, 30f), 1e-4f)
    }

    @Test
    fun gradientIsAUnitOutwardNormal() {
        val random = Random(7)
        repeat(500) {
            val x = random.nextFloat() * 260f - 130f
            val y = random.nextFloat() * 80f - 40f
            val g = GlassGeometry.gradRoundRect(x, y, 100f, 30f, 30f)
            assertEquals(1f, sqrt(g[0] * g[0] + g[1] * g[1]), 1e-4f)
        }
        val top = GlassGeometry.gradRoundRect(0f, -29f, 100f, 30f, 30f)
        assertEquals(0f, top[0], 0f)
        assertEquals(-1f, top[1], 0f)
        val end = GlassGeometry.gradRoundRect(120f, 0f, 100f, 30f, 30f)
        assertEquals(1f, end[0], 1e-4f)
    }

    @Test
    fun sagProfileIsOneAtTheRimAndZeroInside() {
        assertEquals(1f, GlassGeometry.sag(0f, 15f), 1e-6f)
        assertEquals(0f, GlassGeometry.sag(15f, 15f), 1e-6f)
        assertEquals(0f, GlassGeometry.sag(40f, 15f), 1e-6f)
        var previous = 1f
        for (i in 1..15) {
            val s = GlassGeometry.sag(i.toFloat(), 15f)
            assertTrue("sag must decrease monotonically", s < previous)
            previous = s
        }
    }

    @Test
    fun rubberBandStaysInsideTheLimit() {
        assertEquals(1.5f, GlassGeometry.rubberBand(1.5f, 0f, 3f, 0.3f), 0f)
        val near = GlassGeometry.rubberBand(3.1f, 0f, 3f, 0.3f)
        assertTrue("resists: moves less than the finger", near > 3f && near < 3.1f)
        val far = GlassGeometry.rubberBand(10f, 0f, 3f, 0.3f)
        assertTrue(far > 3f && far <= 3.3f)
        val below = GlassGeometry.rubberBand(-10f, 0f, 3f, 0.3f)
        assertTrue(below < 0f && below >= -0.3f)
    }

    // ---- Color ----

    @Test
    fun vibrantMatrixMatchesThePerPixelFormula() {
        val random = Random(3)
        val m = GlassConstants.LensRimLight
        val matrix = GlassMaterial.vibrantMatrix(m)
        repeat(200) {
            val r = random.nextFloat()
            val g = random.nextFloat()
            val b = random.nextFloat()
            val l = GlassMaterial.luma(r, g, b)
            val expected = floatArrayOf(r, g, b).map { m[0] * (it - l) + m[1] * l + m[2] }
            val actual = GlassMaterial.apply(matrix, r, g, b)
            for (i in 0 until 3) assertEquals(expected[i], actual[i], 1e-4f)
        }
    }

    @Test
    fun saturationAndWashMatchThePerPixelFormula() {
        val random = Random(11)
        val saturation = 1.5f
        val wash = 0.6f
        val matrix = GlassMaterial.saturationMatrix(saturation, wash)
        repeat(200) {
            val c = floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat())
            val l = GlassMaterial.luma(c[0], c[1], c[2])
            val saturated = c.map { l + (it - l) * saturation }
            val expected = saturated.map { it + wash * (0.2f * (it - l) + 0.05f) }
            val actual = GlassMaterial.apply(matrix, c[0], c[1], c[2])
            for (i in 0 until 3) assertEquals(expected[i], actual[i], 1e-4f)
        }
    }

    @Test
    fun frostMatrixRemapsLumaAndKeepsChroma() {
        val random = Random(5)
        val saturation = 1.3f
        val black = 0.47f
        val white = 1.03f
        val chroma = 0.8f
        val matrix = GlassMaterial.frostMatrix(saturation, black, white, chroma)
        repeat(200) {
            val c = floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat())
            val l = GlassMaterial.luma(c[0], c[1], c[2])
            val expected = c.map { black + (white - black) * l + chroma * saturation * (it - l) }
            val actual = GlassMaterial.apply(matrix, c[0], c[1], c[2])
            for (i in 0 until 3) assertEquals(expected[i], actual[i], 1e-4f)
        }
    }

    @Test
    fun neutralSaturationIsIdentity() {
        val identity = GlassMaterial.saturationMatrix(1f, 0f)
        val out = GlassMaterial.apply(identity, 0.2f, 0.5f, 0.9f)
        assertEquals(0.2f, out[0], 1e-5f)
        assertEquals(0.5f, out[1], 1e-5f)
        assertEquals(0.9f, out[2], 1e-5f)
    }

    @Test
    fun lumaUsesRec709() {
        assertEquals(1f, GlassMaterial.luma(1f, 1f, 1f), 1e-5f)
        assertEquals(0.7152f, GlassMaterial.luma(0f, 1f, 0f), 1e-5f)
    }

    @Test
    fun faceFormulasMatchUIKit() {
        assertEquals(0.966f, GlassMaterial.faceWhite(0.5f, systemDark = false), 1e-4f)
        assertEquals(0.9f, GlassMaterial.faceWhite(1f, systemDark = true), 1e-4f)
        assertEquals(0.1f, GlassMaterial.faceBlack(0f), 1e-4f)
        assertEquals(0.6f, GlassMaterial.fillAlphaLight(0f), 1e-4f)
        assertEquals(0.25f, GlassMaterial.fillAlphaLight(1f), 1e-4f)
        assertEquals(0.25f, GlassMaterial.fillAlphaDark(0f), 1e-4f)
        assertEquals(0.5f, GlassMaterial.fillAlphaDark(1f), 1e-4f)
    }
}
