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

import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.slaviboy.liquidglass.backdrop.LiquidGlassBackdrop
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.effect.FrostEffectCache
import com.slaviboy.liquidglass.effect.GlassProgram
import com.slaviboy.liquidglass.effect.GlassPrograms
import com.slaviboy.liquidglass.effect.ShaderUniforms
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where the backdrop sits relative to the glass node, resolved once per frame. */
internal class BackdropSource {
    var layer: GraphicsLayer? = null

    /** Position of the glass node's origin in backdrop coordinates. */
    var offsetX = 0f
    var offsetY = 0f
    var width = 0f
    var height = 0f

    val isAvailable: Boolean get() = layer != null

    /** Resolves the backdrop for a glass node; leaves it unavailable on misuse or detach. */
    fun resolve(backdrop: LiquidGlassBackdrop?, node: LayoutCoordinates?) {
        layer = null
        if (backdrop == null || node == null || !node.isAttached || backdrop.isRecording) return
        val coords = backdrop.coordinates ?: return
        val l = backdrop.layer ?: return
        if (!coords.isAttached) return
        val p = coords.localPositionOf(node, Offset.Zero)
        offsetX = p.x
        offsetY = p.y
        width = coords.size.width.toFloat()
        height = coords.size.height.toFloat()
        layer = l
    }

    /** Pixel frame around [left]..[bottom] grown by [margin], clipped to the backdrop. */
    fun frame(out: GlassFrame, left: Float, top: Float, right: Float, bottom: Float, margin: Float) {
        var l = left - margin
        var t = top - margin
        var r = right + margin
        var b = bottom + margin
        if (isAvailable) {
            l = max(l, -offsetX)
            t = max(t, -offsetY)
            r = min(r, -offsetX + width)
            b = min(b, -offsetY + height)
        }
        out.left = floor(l).toInt()
        out.top = floor(t).toInt()
        out.width = max(0, ceil(r).toInt() - out.left)
        out.height = max(0, ceil(b).toInt() - out.top)
    }
}

/** Everything that describes how one glass surface is painted this frame (pixels). */
internal class SurfaceParams {
    var tier = RenderTier.Basic
    var blurPx = 0f
    var saturation = 1f
    var refractPx = 0f
    var refractHeightPx = 1f
    var frostDepthPx = 1f
    var mapBlack = 0f
    var mapWhite = 1f
    var mapChroma = 1f
    var fillGray = 1f
    var fillAlpha = 0.5f
    var flatGray = 0.95f
    var dim = 0f
    var tint: Color = Color.Unspecified
    var tintIntensity = 0f
    var lightAngleRad = 0.785f
    var rimWidthPx = 1f
    var rimIntensity = 1f
    var specular = 0.5f
    var specularFalloff = 2f
    var rimMatrix: FloatArray = GlassConstants.PlatterRimLight
    var press = 0f
    var darkGlass = false
    var contrastBorder = false
    var borderWidthPx = 1f
    var glowX = 0f
    var glowY = 0f
    var glowScale = 1f
    var glowAlpha = 0f
    var glowRadiusPx = 0f
    var shadowAlpha = 0f
    var shadowRadiusPx = 0f
    var shadowOffsetPx = 0f

    fun setFace(face: com.slaviboy.liquidglass.config.GlassFace) {
        mapBlack = face.black
        mapWhite = face.white
        mapChroma = face.chroma
        fillGray = face.fillGray
        fillAlpha = face.fillAlpha
        flatGray = face.flatGray()
        dim = face.dim
    }
}

/**
 * Paints one rounded-rect glass surface (the tab bar platter or any [liquidGlass] element) into
 * [out], an offscreen layer that the tab bar lens also samples.
 */
internal class GlassSurfacePainter(private val graphicsContext: GraphicsContext) {

    // The frosted backdrop. Its content only changes when the glass moves, so the GPU keeps the blur
    // cached while the shader layer above it animates.
    private val blur: GraphicsLayer = graphicsContext.createGraphicsLayer()
    private val fx: GraphicsLayer = graphicsContext.createGraphicsLayer()
    val out: GraphicsLayer = graphicsContext.createGraphicsLayer()
    val path = Path()
    val uniforms = ShaderUniforms()
    var programs: GlassPrograms? = null

    private val frost: Any? = if (Build.VERSION.SDK_INT >= 31) FrostEffectCache() else null
    private val shadow = GlassShadowPainter()
    private val rim = RimPainter()
    private val glow = GlowPainter()
    private var released = false
    private val recordedFrame = GlassFrame()
    private var recordedOffsetX = Float.NaN
    private var recordedOffsetY = Float.NaN
    private var recordedBackdrop: GraphicsLayer? = null
    private var recordedTier: RenderTier? = null

    fun release() {
        if (released) return
        released = true
        graphicsContext.releaseGraphicsLayer(blur)
        graphicsContext.releaseGraphicsLayer(fx)
        graphicsContext.releaseGraphicsLayer(out)
    }

    /** The tier that can actually be drawn with the available backdrop and programs. */
    fun effectiveTier(requested: RenderTier, src: BackdropSource): RenderTier = when {
        requested == RenderTier.Full && src.isAvailable && programs != null && Build.VERSION.SDK_INT >= 33 -> RenderTier.Full
        (requested == RenderTier.Full || requested == RenderTier.Blur) && src.isAvailable && Build.VERSION.SDK_INT >= 31 -> RenderTier.Blur
        else -> RenderTier.Basic
    }

    fun updatePath(rect: GlassRect) {
        path.rewind()
        path.addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom, CornerRadius(rect.r)))
    }

    /** Shadow + surface. Returns the tier that was used. */
    fun draw(scope: DrawScope, rect: GlassRect, frame: GlassFrame, src: BackdropSource, p: SurfaceParams): RenderTier {
        updatePath(rect)
        shadow.draw(scope, rect, path, p.shadowOffsetPx, p.shadowRadiusPx, p.shadowAlpha)
        val tier = recordSurface(scope, rect, frame, src, p)
        scope.drawLayer(out)
        // The fingertip glow lights the bar around the finger. It is not part of [out], so the lens
        // (which samples [out]) stays clear.
        if (p.glowAlpha > 0.001f) {
            scope.clipPath(path) { glow.draw(this, p.glowX, p.glowY, p.glowRadiusPx, p.glowScale, p.glowAlpha) }
        }
        return tier
    }

    /** Records the finished surface into [out] (positioned at [frame]); [updatePath] first. */
    fun recordSurface(scope: DrawScope, rect: GlassRect, frame: GlassFrame, src: BackdropSource, p: SurfaceParams): RenderTier {
        val tier = effectiveTier(p.tier, src)
        if (frame.isEmpty) return tier
        with(scope) {
            if (tier == RenderTier.Full && Build.VERSION.SDK_INT >= 33) {
                fillUniforms(rect, frame, p)
                val program = programs!!.platter as GlassProgram
                blur.renderEffect = (frost as FrostEffectCache).composeFrost(p.blurPx, p.saturation)
                if (needsRecording(frame, src, tier)) {
                    recordBackdrop(blur, frame, src)
                    fx.topLeft = IntOffset(frame.left, frame.top)
                    fx.record(IntSize(frame.width, frame.height)) {
                        translate(-frame.left.toFloat(), -frame.top.toFloat()) { drawLayer(blur) }
                    }
                }
                fx.renderEffect = program.renderEffect(uniforms, null)
            } else if (tier == RenderTier.Blur && Build.VERSION.SDK_INT >= 31) {
                fx.renderEffect = (frost as FrostEffectCache)
                    .composeFrost(p.blurPx, p.saturation, p.mapBlack, p.mapWhite, p.mapChroma)
                if (needsRecording(frame, src, tier)) recordBackdrop(fx, frame, src)
            }

            out.topLeft = IntOffset(frame.left, frame.top)
            out.record(IntSize(frame.width, frame.height)) {
                translate(-frame.left.toFloat(), -frame.top.toFloat()) {
                    when (tier) {
                        RenderTier.Full -> drawLayer(fx)
                        RenderTier.Blur -> clipPath(path) {
                            drawLayer(fx)
                            drawFace(this, rect, p, basic = false)
                        }
                        else -> clipPath(path) { drawFace(this, rect, p, basic = true) }
                    }
                    if (tier != RenderTier.Full) {
                        val rimAlpha = (if (p.darkGlass) 0.35f else 0.6f) * p.rimIntensity
                        rim.draw(this, rect, path, p.lightAngleRad, p.specularFalloff, rimAlpha, p.rimWidthPx)
                    }
                    if (p.contrastBorder) {
                        val c = if (p.darkGlass) Color.White else Color.Black
                        clipPath(path) { drawPath(path, c.copy(alpha = 0.5f), style = Stroke(p.borderWidthPx * 2f)) }
                    }
                }
            }
        }
        return tier
    }

    private fun fillUniforms(rect: GlassRect, frame: GlassFrame, p: SurfaceParams) {
        val u = uniforms
        u.cx = rect.cx - frame.left
        u.cy = rect.cy - frame.top
        u.hw = rect.hw
        u.hh = rect.hh
        u.radius = rect.r
        u.clampLeft = 0.5f
        u.clampTop = 0.5f
        u.clampRight = frame.width - 0.5f
        u.clampBottom = frame.height - 0.5f
        u.oval = GlassConstants.Ovalization
        u.refract = p.refractPx
        u.refractHeight = max(p.refractHeightPx, 1f)
        u.mapBlack = p.mapBlack
        u.mapWhite = p.mapWhite
        u.mapChroma = p.mapChroma
        u.fillGray = p.fillGray
        u.fillAlpha = p.fillAlpha
        u.frostEdge = GlassConstants.FrostEdge
        u.frostDepth = max(p.frostDepthPx, 1f)
        if (p.tint.isSpecified && p.tintIntensity > 0f) {
            u.tintR = p.tint.red
            u.tintG = p.tint.green
            u.tintB = p.tint.blue
            u.tintA = p.tintIntensity
        } else {
            u.tintR = 0f; u.tintG = 0f; u.tintB = 0f; u.tintA = 0f
        }
        u.dim = p.dim
        u.lightX = cos(p.lightAngleRad)
        u.lightY = sin(p.lightAngleRad)
        u.rimWidth = max(p.rimWidthPx, 0.5f)
        u.rimCurvature = GlassConstants.PlatterRimCurvature
        u.rimIntensity = p.rimIntensity
        u.specularFalloff = p.specularFalloff
        u.setRimMatrix(p.rimMatrix)
        u.specular = p.specular
        u.press = p.press
    }

    /** Whether the backdrop layers must be re-recorded (the glass moved or the source changed). */
    private fun needsRecording(frame: GlassFrame, src: BackdropSource, tier: RenderTier): Boolean {
        val same = recordedTier == tier && recordedBackdrop === src.layer &&
            recordedOffsetX == src.offsetX && recordedOffsetY == src.offsetY &&
            recordedFrame.left == frame.left && recordedFrame.top == frame.top &&
            recordedFrame.width == frame.width && recordedFrame.height == frame.height
        if (same) return false
        recordedTier = tier
        recordedBackdrop = src.layer
        recordedOffsetX = src.offsetX
        recordedOffsetY = src.offsetY
        recordedFrame.left = frame.left
        recordedFrame.top = frame.top
        recordedFrame.width = frame.width
        recordedFrame.height = frame.height
        return true
    }

    private fun DrawScope.recordBackdrop(layer: GraphicsLayer, frame: GlassFrame, src: BackdropSource) {
        val backdrop = src.layer ?: return
        layer.topLeft = IntOffset(frame.left, frame.top)
        layer.record(IntSize(frame.width, frame.height)) {
            translate(-(frame.left + src.offsetX), -(frame.top + src.offsetY)) {
                drawLayer(backdrop)
            }
        }
    }

    /** Frosted face for the Blur and Basic tiers, drawn inside the clip. */
    private fun drawFace(scope: DrawScope, rect: GlassRect, p: SurfaceParams, basic: Boolean) = with(scope) {
        if (basic) {
            // No backdrop: the average face color, nearly opaque.
            drawRect(Color(p.flatGray, p.flatGray, p.flatGray), alpha = 0.9f * (1f - if (p.tint.isSpecified) p.tintIntensity else 0f))
        } else {
            drawRect(Color(p.fillGray, p.fillGray, p.fillGray), alpha = p.fillAlpha.coerceIn(0f, 1f))
        }
        if (p.tint.isSpecified && p.tintIntensity > 0f) {
            drawRect(p.tint, alpha = if (basic) p.tintIntensity else p.tintIntensity * 0.85f)
        }
        if (p.dim > 0f) drawRect(Color.Black, alpha = p.dim)
        if (p.press > 0f) {
            drawRect(Color.White, alpha = (0.05f * GlassConstants.PressWash * p.press).coerceIn(0f, 1f))
        }
        if (basic) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (p.darkGlass) 0.08f else 0.22f),
                    1f to Color.Transparent,
                    startY = rect.top,
                    endY = rect.cy
                )
            )
        }
    }
}
