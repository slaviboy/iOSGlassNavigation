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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
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
import com.slaviboy.liquidglass.system.BackdropLumaSampler
import com.slaviboy.liquidglass.system.rememberAdaptiveGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Turns any composable into a piece of Liquid Glass that refracts [backdrop]: the same material
 * as the tab bar (refraction, frost, adaptive light/dark, lit rim, shadow), drawn behind the
 * content.
 *
 * The glass is a rounded rectangle: rounded and rectangular [shape]s are reproduced exactly,
 * other shapes are approximated by their bounds. The element must not be inside the content
 * marked with [com.slaviboy.liquidglass.backdrop.liquidGlassBackdrop].
 *
 * @param interactive swell, wash and glow under the finger like iOS `.interactive()` glass.
 *   Touches are observed, not consumed, so clickable content keeps working.
 * @param onDarkGlassChange called with true when the glass turns dark (an adaptive flip or a dark
 *   tint), so content on it should switch to light symbols. See [LiquidGlassBox].
 */
fun Modifier.liquidGlass(
    backdrop: LiquidGlassBackdrop,
    shape: Shape = RoundedCornerShape(percent = 50),
    style: LiquidGlassStyle? = null,
    settings: LiquidGlassSettings? = null,
    interactive: Boolean = false,
    onDarkGlassChange: ((dark: Boolean) -> Unit)? = null
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "liquidGlass"
        properties["backdrop"] = backdrop
        properties["shape"] = shape
        properties["interactive"] = interactive
    }
) {
    val currentSettings = settings ?: LocalLiquidGlassSettings.current
    val currentStyle = style ?: LocalLiquidGlassStyle.current
    val environment = currentGlassEnvironment(currentSettings)
    val system by environment.system
    val resolved = remember(currentSettings, currentStyle, system, environment.deviceMaxTier) {
        resolveGlass(currentSettings, currentStyle, system, environment.deviceMaxTier)
    }
    val density = LocalDensity.current
    val systemDark = isSystemInDarkTheme()
    val graphicsContext = LocalGraphicsContext.current
    val painter = remember(graphicsContext) { GlassSurfacePainter(graphicsContext) }
    val sampler = remember(graphicsContext) { BackdropLumaSampler(graphicsContext) }
    DisposableEffect(painter, sampler) {
        onDispose {
            painter.release()
            sampler.release()
        }
    }
    val programsReady = remember(painter) { mutableStateOf(painter.programs != null) }
    LaunchedEffect(painter, resolved.tier) {
        if (resolved.tier == RenderTier.Full && painter.programs == null) {
            val compiled = withContext(Dispatchers.Default) { GlassPrograms.compile(withLens = false) }
            painter.programs = compiled
            programsReady.value = compiled != null
        }
    }

    val holder = remember { CoordinatesHolder() }
    val adaptive = rememberAdaptiveGlass(
        currentSettings.colorScheme, resolved.adaptive, systemDark, resolved.clearVariant
    ) {
        sampler.sample(backdrop, holder.coordinates, density)
    }
    val currentOnDarkGlassChange by rememberUpdatedState(onDarkGlassChange)
    val tintIsDark = resolved.hasTint && resolved.tintIntensity > 0.4f && GlassMaterial.luma(resolved.tintColor) < 0.6f
    LaunchedEffect(adaptive, tintIsDark) {
        snapshotFlow { adaptive.isDark || tintIsDark }.collect { currentOnDarkGlassChange?.invoke(it) }
    }
    val face = remember { GlassFace() }
    val params = remember { SurfaceParams() }
    val rect = remember { GlassRect() }
    val frame = remember { GlassFrame() }
    val source = remember { BackdropSource() }
    val press = remember { Animatable(0f) }
    val pressPosition = remember { mutableStateOf(Offset.Zero) }
    val positionTick = remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val tilt = environment.tilt

    val touch = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressPosition.value = down.position
                scope.launch { press.animateTo(1f, spring(dampingRatio = 0.865f, stiffness = 1205f)) }
                waitForUpOrCancellation(PointerEventPass.Initial)
                scope.launch { press.animateTo(0f, spring(dampingRatio = 1f, stiffness = 158.6f)) }
            }
        }
    } else {
        Modifier
    }

    this
        // Own layer, so redraws of this glass (press, content changes) never re-record the backdrop.
        .graphicsLayer()
        .onGloballyPositioned {
            holder.coordinates = it
            val p = it.positionInRoot()
            if (p != holder.lastPosition) {
                holder.lastPosition = p
                positionTick.intValue++
            }
        }
        .then(touch)
        .drawWithContent {
            if (resolved.identity) {
                drawContent()
                return@drawWithContent
            }
            positionTick.intValue
            backdrop.version.intValue
            programsReady.value
            val pressed = press.value
            val swell = if (resolved.reduceMotion) 1f else 1f + GlassConstants.Swell * pressed

            val radius = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rounded -> min(outline.roundRect.topLeftCornerRadius.x, outline.roundRect.bottomRightCornerRadius.x)
                else -> 0f
            }
            rect.set(size.width / 2f, size.height / 2f, size.width / 2f * swell, size.height / 2f * swell, radius * swell)
            source.resolve(backdrop, holder.coordinates)
            source.frame(frame, rect.left, rect.top, rect.right, rect.bottom, 24.dp.toPx())

            face.update(resolved, adaptive.luma.value, adaptive.isDark, systemDark)
            params.tier = resolved.tier
            params.blurPx = resolved.platter.blurRadius.toPx()
            params.saturation = resolved.vibrancy
            params.refractPx = resolved.platter.refractionAmount.toPx()
            params.refractHeightPx = min(resolved.platter.refractionHeight.toPx(), min(rect.hw, rect.hh))
            params.frostDepthPx = GlassConstants.FrostFullDepth.dp.toPx()
            params.setFace(face)
            params.tint = resolved.tintColor
            params.tintIntensity = if (resolved.hasTint) resolved.tintIntensity else 0f
            val angle = resolved.specularAngle + if (resolved.deviceTilt) tilt.value else 0f
            params.lightAngleRad = Math.toRadians(angle.toDouble()).toFloat()
            params.rimWidthPx = resolved.rimWidth.toPx()
            params.rimIntensity = resolved.specularIntensity
            params.specular = 0.35f * resolved.specularIntensity
            params.specularFalloff = resolved.specularFalloff
            params.rimMatrix = if (adaptive.isDark) GlassConstants.PlatterRimDark else GlassConstants.PlatterRimLight
            params.press = pressed
            params.darkGlass = adaptive.isDark
            params.contrastBorder = resolved.increaseContrast
            params.borderWidthPx = 1.dp.toPx()
            val position = pressPosition.value
            params.glowX = position.x
            params.glowY = position.y
            params.glowScale = 1f + pressed
            params.glowAlpha = if (interactive && !resolved.reduceTransparency) GlassConstants.GlowOpacity * pressed else 0f
            params.glowRadiusPx = GlassConstants.GlowDiameter.dp.toPx()
            params.shadowAlpha = resolved.shadow.alpha
            params.shadowRadiusPx = resolved.shadow.radius.toPx()
            params.shadowOffsetPx = resolved.shadow.offsetY.toPx()

            painter.draw(this, rect, frame, source, params)
            if (swell != 1f) {
                scale(swell) { this@drawWithContent.drawContent() }
            } else {
                drawContent()
            }
        }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
    var lastPosition = Offset.Unspecified
}
