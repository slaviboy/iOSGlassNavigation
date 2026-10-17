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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slaviboy.liquidglass.config.GlassAppearance
import com.slaviboy.liquidglass.config.GlassColorScheme
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.config.GlassFace
import com.slaviboy.liquidglass.config.GlassVariant
import com.slaviboy.liquidglass.config.LiquidGlassAccessibility
import com.slaviboy.liquidglass.config.LiquidGlassDefaults
import com.slaviboy.liquidglass.config.LiquidGlassSettings
import com.slaviboy.liquidglass.config.LiquidGlassStyle
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.config.SystemAccessibility
import com.slaviboy.liquidglass.config.SystemFollow
import com.slaviboy.liquidglass.config.downgradeTier
import com.slaviboy.liquidglass.config.resolveGlass
import com.slaviboy.liquidglass.system.AdaptiveAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAndSettingsTest {

    // ---- Adaptive light/dark ----

    @Test
    fun lumaIsQuantizedTo32Steps() {
        assertEquals(0.5f, AdaptiveAppearance.quantize(0.51f), 0f)
        assertEquals(1f, AdaptiveAppearance.quantize(1.4f), 0f)
        assertEquals(0f, AdaptiveAppearance.quantize(-1f), 0f)
    }

    @Test
    fun lightModeHysteresis() {
        val adaptive = AdaptiveAppearance(systemDark = false)
        assertFalse(adaptive.isDark)
        assertFalse("mid gray keeps the glass light", adaptive.update(0.3f))
        assertTrue("dark content flips it", adaptive.update(0.15f))
        assertTrue("not bright enough to flip back", adaptive.update(0.4f))
        assertFalse(adaptive.update(0.6f))
    }

    @Test
    fun darkModeHysteresis() {
        val adaptive = AdaptiveAppearance(systemDark = true)
        assertTrue(adaptive.isDark)
        assertTrue(adaptive.update(0.8f))
        assertFalse("very bright content flips dark glass to light", adaptive.update(0.95f))
        assertFalse(adaptive.update(0.65f))
        assertTrue(adaptive.update(0.5f))
    }

    @Test
    fun systemModeChangeResetsTheGlass() {
        val adaptive = AdaptiveAppearance(systemDark = false)
        adaptive.systemDark = true
        assertTrue(adaptive.isDark)
    }

    // ---- Tiers ----

    @Test
    fun tierCanOnlyBeDowngraded() {
        assertEquals(RenderTier.Full, downgradeTier(RenderTier.Auto, RenderTier.Full))
        assertEquals(RenderTier.Blur, downgradeTier(RenderTier.Auto, RenderTier.Blur))
        assertEquals(RenderTier.Blur, downgradeTier(RenderTier.Full, RenderTier.Blur))
        assertEquals(RenderTier.Basic, downgradeTier(RenderTier.Basic, RenderTier.Full))
        assertEquals(RenderTier.Blur, downgradeTier(RenderTier.Blur, RenderTier.Full))
        assertEquals(RenderTier.Basic, downgradeTier(RenderTier.Full, RenderTier.Basic))
    }

    // ---- Settings resolution ----

    private val style = LiquidGlassStyle()
    private val noSystem = SystemAccessibility()

    /** iOS's own defaults (the library defaults to clear, dark glass). */
    private val ios = LiquidGlassSettings(variant = GlassVariant.Regular, colorScheme = GlassColorScheme.Adaptive)

    @Test
    fun defaultsAreClearDarkGlass() {
        val settings = LiquidGlassSettings()
        assertEquals(GlassVariant.Clear, settings.variant)
        assertEquals(GlassColorScheme.Dark, settings.colorScheme)
        val r = resolveGlass(settings, style, noSystem, RenderTier.Full)
        assertTrue(r.clearVariant)
        assertTrue(r.fixedDark)
        assertFalse("a fixed scheme never flips", r.adaptive)
    }

    @Test
    fun reduceTransparencyRemovesRefractionAndFrosts() {
        val settings = LiquidGlassSettings(accessibility = LiquidGlassAccessibility(reduceTransparency = true))
        val r = resolveGlass(settings, style, noSystem, RenderTier.Full)
        assertTrue(r.reduceTransparency)
        assertEquals(0.dp, r.platter.refractionAmount)
        assertEquals(GlassConstants.ReduceTransparencyBlur.dp, r.platter.blurRadius)
        assertEquals(0.dp, r.lens.warpAmount)
        assertEquals(0f, r.lens.chromaticAberration, 0f)
        assertFalse("no adaptive flipping", r.adaptive)
    }

    @Test
    fun systemSettingsAreFollowedOnlyWhenAsked() {
        val system = SystemAccessibility(reduceMotion = true, increaseContrast = true, reduceTransparency = true)
        val following = resolveGlass(LiquidGlassSettings(), style, system, RenderTier.Full)
        assertTrue(following.reduceMotion)
        assertTrue(following.increaseContrast)
        assertFalse("window blur mapping is opt-in", following.reduceTransparency)

        val ignoring = resolveGlass(LiquidGlassSettings(followSystem = SystemFollow.None), style, system, RenderTier.Full)
        assertFalse(ignoring.reduceMotion)
        assertFalse(ignoring.increaseContrast)
    }

    @Test
    fun increaseContrastDropsTintAndUsesContrastBlue() {
        val settings = LiquidGlassSettings(accessibility = LiquidGlassAccessibility(increaseContrast = true))
        val r = resolveGlass(settings, style.copy(tintColor = Color.Red), noSystem, RenderTier.Full)
        assertFalse(r.hasTint)
        assertEquals(LiquidGlassDefaults.AccentContrast, r.accentColor)
    }

    @Test
    fun reduceMotionDisablesTilt() {
        val settings = LiquidGlassSettings(accessibility = LiquidGlassAccessibility(reduceMotion = true))
        assertFalse(resolveGlass(settings, style, noSystem, RenderTier.Full).deviceTilt)
        assertTrue(resolveGlass(LiquidGlassSettings(followSystem = SystemFollow.None), style, noSystem, RenderTier.Full).deviceTilt)
    }

    @Test
    fun clearVariantIsBarelyFrosted() {
        val r = resolveGlass(LiquidGlassSettings(variant = GlassVariant.Clear), style, noSystem, RenderTier.Full)
        assertTrue(r.clearVariant)
        assertTrue(r.platter.blurRadius <= 1.dp)
        assertEquals(0.2f, r.frost, 1e-5f)
    }

    @Test
    fun identityVariantIsFlagged() {
        assertTrue(resolveGlass(LiquidGlassSettings(variant = GlassVariant.Identity), style, noSystem, RenderTier.Full).identity)
    }

    @Test
    fun forcedTierIsRespected() {
        val r = resolveGlass(LiquidGlassSettings(renderTier = RenderTier.Basic), style, noSystem, RenderTier.Full)
        assertEquals(RenderTier.Basic, r.tier)
    }

    // ---- Face ----

    @Test
    fun tintedAppearanceIsMoreOpaque() {
        val clear = GlassFace()
        val tinted = GlassFace()
        clear.update(resolveGlass(ios, style, noSystem, RenderTier.Full), 0.6f, false, false)
        tinted.update(
            resolveGlass(ios.copy(appearance = GlassAppearance.Tinted), style, noSystem, RenderTier.Full),
            0.6f, false, false
        )
        assertEquals(clear.fillAlpha + 0.15f, tinted.fillAlpha, 1e-4f)
    }

    @Test
    fun tintedAppearanceAlsoAppliesToClearGlass() {
        val clearGlass = LiquidGlassSettings(variant = GlassVariant.Clear)
        val plain = GlassFace()
        val tinted = GlassFace()
        plain.update(resolveGlass(clearGlass, style, noSystem, RenderTier.Full), 0.6f, true, false)
        tinted.update(
            resolveGlass(clearGlass.copy(appearance = GlassAppearance.Tinted), style, noSystem, RenderTier.Full),
            0.6f, true, false
        )
        assertEquals(plain.fillAlpha + 0.15f, tinted.fillAlpha, 1e-4f)
    }

    @Test
    fun increaseContrastFaceIsSolidWhiteOrBlack() {
        val r = resolveGlass(
            LiquidGlassSettings(accessibility = LiquidGlassAccessibility(increaseContrast = true)),
            style, noSystem, RenderTier.Full
        )
        val face = GlassFace()
        face.update(r, 0.3f, darkGlass = false, systemDark = false)
        assertEquals(1f, face.fillGray, 0f)
        assertEquals(GlassConstants.IncreaseContrastFill, face.fillAlpha, 0f)
        face.update(r, 0.3f, darkGlass = true, systemDark = false)
        assertEquals(0f, face.fillGray, 0f)
    }

    @Test
    fun tintThinsTheFrostedFace() {
        val plain = GlassFace()
        val tinted = GlassFace()
        plain.update(resolveGlass(LiquidGlassSettings(), style, noSystem, RenderTier.Full), 0.6f, false, false)
        tinted.update(
            resolveGlass(LiquidGlassSettings(), style.copy(tintColor = Color.Blue, tintIntensity = 0.9f), noSystem, RenderTier.Full),
            0.6f, false, false
        )
        assertEquals(plain.fillAlpha * 0.1f, tinted.fillAlpha, 1e-4f)
    }

    @Test
    fun faceRemapsLumaIntoABrightRangeOnLightGlass() {
        val face = GlassFace()
        face.update(resolveGlass(ios, style, noSystem, RenderTier.Full), 0.6f, false, false)
        assertEquals(1.032f * 0.6f - 0.15f, face.black, 1e-4f)
        assertEquals(1.03f, face.white, 1e-4f)
        assertEquals(1f, face.chroma, 0f)
        assertTrue("light glass stays light over mid content", face.flatGray() > 0.8f)
    }

    @Test
    fun reduceTransparencyMapsEveryLumaToOneLevel() {
        val r = resolveGlass(
            LiquidGlassSettings(accessibility = LiquidGlassAccessibility(reduceTransparency = true)),
            style, noSystem, RenderTier.Full
        )
        val face = GlassFace()
        face.update(r, 0.2f, darkGlass = false, systemDark = false)
        assertEquals(face.black, face.white, 0f)
        assertEquals(GlassConstants.ReduceTransparencyLuma, face.white, 0f)
        assertEquals(0.9f / 1.1f, face.chroma, 1e-5f)
    }

    @Test
    fun clearVariantDimsBrightContent() {
        val face = GlassFace()
        face.update(resolveGlass(LiquidGlassSettings(variant = GlassVariant.Clear), style, noSystem, RenderTier.Full), 0.9f, false, false)
        assertEquals(0.35f, face.dim, 1e-4f)
        face.update(resolveGlass(LiquidGlassSettings(variant = GlassVariant.Clear), style, noSystem, RenderTier.Full), 0.2f, false, false)
        assertEquals(0f, face.dim, 1e-4f)
    }
}
