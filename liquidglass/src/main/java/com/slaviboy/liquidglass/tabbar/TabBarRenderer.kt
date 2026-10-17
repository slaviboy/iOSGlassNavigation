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

import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.slaviboy.liquidglass.backdrop.LiquidGlassBackdrop
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.config.GlassFace
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.config.ResolvedGlass
import com.slaviboy.liquidglass.effect.FrostEffectCache
import com.slaviboy.liquidglass.effect.GlassProgram
import com.slaviboy.liquidglass.effect.GlassPrograms
import com.slaviboy.liquidglass.effect.ShaderUniforms
import com.slaviboy.liquidglass.glass.BackdropSource
import com.slaviboy.liquidglass.glass.GlassFrame
import com.slaviboy.liquidglass.glass.GlassGeometry.lerp
import com.slaviboy.liquidglass.glass.GlassMaterial
import com.slaviboy.liquidglass.glass.GlassRect
import com.slaviboy.liquidglass.glass.GlassShadowPainter
import com.slaviboy.liquidglass.glass.GlassSurfacePainter
import com.slaviboy.liquidglass.glass.RimPainter
import com.slaviboy.liquidglass.physics.TabBarPhysics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Live per-frame inputs of the tab bar drawing, filled by the draw lambda. */
internal class TabBarDrawInput {
    lateinit var physics: TabBarPhysics
    lateinit var resolved: ResolvedGlass
    lateinit var face: GlassFace
    var backdrop: LiquidGlassBackdrop? = null
    var darkGlass = false
    var lightAngleDeg = 45f
    var minimizeProgress = 0f
    var fullWidth = 0f
    var baseHeight = 0f
    var inset = 0f
    var lensGrow = 0f
    var selectionSize = 0f
    var rowOffsetX = 0f
}

/**
 * Draws the tab bar: the platter, the resting selection pill, the content (tab row) and the clear
 * lens that lifts out of the bar while pressing. The lens samples only layers that never contain
 * the lens itself (backdrop, platter, accent copy of the row), so there is no feedback loop.
 */
internal class TabBarRenderer(private val graphicsContext: GraphicsContext) {

    val surface = GlassSurfacePainter(graphicsContext)

    /** The hidden accent-colored copy of the tab row, recorded by the row, drawn only in the lens. */
    val accent: GraphicsLayer = graphicsContext.createGraphicsLayer()
    private val pill = graphicsContext.createGraphicsLayer()
    private val lens = graphicsContext.createGraphicsLayer()

    var coordinates: LayoutCoordinates? = null
    var programs: GlassPrograms?
        get() = surface.programs
        set(value) {
            surface.programs = value
        }

    private val lensFrost: Any? = if (Build.VERSION.SDK_INT >= 31) FrostEffectCache() else null
    private val lensUniforms = ShaderUniforms()
    private val pillPath = Path()
    private val lensPath = Path()
    private val lensShadow = GlassShadowPainter()
    private val lensRim = RimPainter()
    private val src = BackdropSource()
    private val frame = GlassFrame()
    private val lensFrame = GlassFrame()
    private val platterRect = GlassRect()
    private val pillRect = GlassRect()
    private val lensRect = GlassRect()
    private val params = com.slaviboy.liquidglass.glass.SurfaceParams()
    private val pillMatrix = FloatArray(20)
    private var pillKey: FloatArray? = null
    private var pillFilter: ColorFilter? = null
    private var released = false

    // Bar transform of the current frame (swell + stretch), reused by the accent copy.
    private var barScaleX = 1f
    private var barScaleY = 1f
    private var barDriftX = 0f
    private var barDriftY = 0f

    fun release() {
        if (released) return
        released = true
        surface.release()
        graphicsContext.releaseGraphicsLayer(accent)
        graphicsContext.releaseGraphicsLayer(pill)
        graphicsContext.releaseGraphicsLayer(lens)
    }

    fun draw(scope: ContentDrawScope, input: TabBarDrawInput) = with(scope) {
        val r = input.resolved
        if (r.identity || released) {
            drawContent()
            return@with
        }
        val ph = input.physics
        val w = size.width
        val h = size.height
        src.resolve(input.backdrop, coordinates)

        // ---- Platter: swell while pressed, stretch toward the finger (area preserved). ----
        val swell = 1f + GlassConstants.Swell * ph.swell.value
        val sxr = ph.stretchX.value
        val syr = ph.stretchY.value
        val e = abs(sxr) - abs(syr)
        barScaleX = swell * (1f + e)
        barScaleY = swell / (1f + e)
        barDriftX = GlassConstants.StretchDrift * sxr * w * 0.5f
        barDriftY = GlassConstants.StretchDrift * syr * h * 0.5f
        val bcx = w / 2f + barDriftX
        val bcy = h / 2f + barDriftY
        platterRect.set(bcx, bcy, w / 2f * barScaleX, h / 2f * barScaleY)

        // ---- Resting pill: under the selected tab, a 48 dp circle when minimized. ----
        val p = input.minimizeProgress.coerceIn(0f, 1f)
        val itemW = ph.itemWidth
        val slotCx = input.rowOffsetX + input.inset + itemW * (ph.lens.value + 0.5f)
        val pillCx = lerp(slotCx, w / 2f, p)
        val pillHw = lerp(itemW / 2f, input.selectionSize / 2f, p)
        val pillHh = lerp(input.baseHeight / 2f - input.inset, input.selectionSize / 2f, p)
        pillRect.set(bcx + (pillCx - w / 2f) * barScaleX, bcy, pillHw * barScaleX, pillHh * barScaleY)

        // ---- Lens: the pill grown 8 dp per side, flexed by acceleration. ----
        val lift = ph.lift.value.coerceIn(0f, 1.2f)
        val lensAlpha = (4f * ph.lift.value).coerceIn(0f, 1f)
        val grow = input.lensGrow * lift
        val m = ph.flexEased.value
        val flexX = 1f + GlassConstants.FlexScaleX * m
        val flexY = (1f - m).coerceIn(GlassConstants.FlexMinScaleY, GlassConstants.FlexMaxScaleY)
        lensRect.set(
            pillRect.cx + GlassConstants.FlexDrift.dp.toPx() * m * ph.flexDirection,
            pillRect.cy,
            (pillRect.hw + grow) * flexX,
            (pillRect.hh + grow) * flexY
        )

        // The platter frame only depends on the bar bounds, so the frosted backdrop stays cached on
        // the GPU while the bar swells; the margin covers the swell and the stretch.
        src.frame(frame, 0f, 0f, w, h, margin = 32.dp.toPx())

        // ---- Platter ----
        fillSurfaceParams(this, input)
        val tier = surface.draw(this, platterRect, frame, src, params)

        // ---- Resting pill (fades out as the lens lifts) ----
        val pillAlpha = 1f - lensAlpha
        if (pillAlpha > 0.001f && !pillRect.isEmpty && !frame.isEmpty) {
            pillPath.rewind()
            pillPath.addRoundRect(RoundRect(pillRect.left, pillRect.top, pillRect.right, pillRect.bottom, CornerRadius(pillRect.r)))
            pill.colorFilter = pillFilter(input.darkGlass)
            pill.alpha = pillAlpha
            // The pill layer only covers the pill, so its color filter pass stays small.
            val pl = floor(pillRect.left).toInt() - 1
            val pt = floor(pillRect.top).toInt() - 1
            pill.topLeft = IntOffset(pl, pt)
            pill.record(IntSize(ceil(pillRect.right).toInt() + 1 - pl, ceil(pillRect.bottom).toInt() + 1 - pt)) {
                translate(-pl.toFloat(), -pt.toFloat()) { drawLayer(surface.out) }
            }
            clipPath(pillPath) { drawLayer(pill) }
        }

        // ---- Tab row ----
        drawContent()

        // ---- Lens ----
        if (lensAlpha > 0.001f && !lensRect.isEmpty) {
            updateLensFrame(input)
            lensPath.rewind()
            lensPath.addRoundRect(RoundRect(lensRect.left, lensRect.top, lensRect.right, lensRect.bottom, CornerRadius(lensRect.r)))
            lensShadow.draw(
                this, lensRect, lensPath,
                r.lensShadow.offsetY.toPx(), r.lensShadow.radius.toPx(), r.lensShadow.alpha * lensAlpha
            )
            val magnification = lerp(1f, r.lensMagnification, lift.coerceIn(0f, 1f))
            if (tier == RenderTier.Full && Build.VERSION.SDK_INT >= 33) {
                fillLensUniforms(this, input, lift, lensAlpha)
                val program = surface.programs!!.lens as GlassProgram
                val inner = (lensFrost as FrostEffectCache).frost(r.lens.blurRadius.toPx(), 1f)
                lens.renderEffect = program.renderEffect(lensUniforms, inner)
                lens.alpha = 1f
                recordLensSource(this, h, input.baseHeight, input.rowOffsetX, magnification, withBackdrop = true)
                drawLayer(lens)
            } else {
                lens.renderEffect = null
                lens.alpha = lensAlpha
                recordLensSource(this, h, input.baseHeight, input.rowOffsetX, magnification, withBackdrop = tier == RenderTier.Blur)
                val bulge = lerp(1f, 1.04f, lift.coerceIn(0f, 1f))
                clipPath(lensPath) {
                    scale(bulge, bulge, pivot = Offset(lensRect.cx, lensRect.cy)) { drawLayer(lens) }
                }
                val rimAlpha = (if (input.darkGlass) 0.45f else 0.8f) * r.specularIntensity * lensAlpha
                lensRim.draw(
                    this, lensRect, lensPath, Math.toRadians(input.lightAngleDeg.toDouble()).toFloat(),
                    r.specularFalloff, rimAlpha, r.rimWidth.toPx() * 1.3f
                )
            }
            if (r.increaseContrast) {
                val c = if (input.darkGlass) Color.White else Color.Black
                clipPath(lensPath) {
                    drawPath(lensPath, c.copy(alpha = 0.5f * lensAlpha), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }

    /** Backdrop + platter + accent copy (magnified around the lens center) in frame space. */
    private fun recordLensSource(
        scope: DrawScope, h: Float, baseHeight: Float, rowX: Float, magnification: Float, withBackdrop: Boolean
    ) =
        with(scope) {
            val backdrop = src.layer
            val w = size.width
            val rowY = (h - baseHeight) / 2f
            val f = lensFrame
            lens.topLeft = IntOffset(f.left, f.top)
            lens.record(IntSize(f.width, f.height)) {
                if (withBackdrop && backdrop != null) {
                    translate(-(f.left + src.offsetX), -(f.top + src.offsetY)) { drawLayer(backdrop) }
                }
                translate(-f.left.toFloat(), -f.top.toFloat()) {
                    drawLayer(surface.out)
                    withTransform({
                        scale(magnification, magnification, pivot = Offset(lensRect.cx, lensRect.cy))
                        translate(barDriftX, barDriftY)
                        scale(barScaleX, barScaleY, pivot = Offset(w / 2f, h / 2f))
                        translate(rowX, rowY)
                    }) {
                        drawLayer(accent)
                    }
                }
            }
        }

    /**
     * A fixed-size frame that follows the lens: the lens shader only runs over the lens, and the
     * layer keeps its size (no reallocation) while it moves.
     */
    private fun DrawScope.updateLensFrame(input: TabBarDrawInput) {
        val itemW = input.physics.itemWidth
        val grow = input.lensGrow * 1.25f
        val maxHw = (max(itemW, input.selectionSize) / 2f + grow) * (1f + GlassConstants.FlexScaleX * GlassConstants.FlexLimit)
        val maxHh = (input.baseHeight / 2f - input.inset + grow) * GlassConstants.FlexMaxScaleY
        val pad = 24.dp.toPx()
        lensFrame.width = ceil(2f * (maxHw + pad)).toInt()
        lensFrame.height = ceil(2f * (maxHh + pad)).toInt()
        lensFrame.left = floor(lensRect.cx - lensFrame.width / 2f).toInt()
        lensFrame.top = floor(lensRect.cy - lensFrame.height / 2f).toInt()
    }

    private fun fillSurfaceParams(scope: DrawScope, input: TabBarDrawInput) = with(scope) {
        val r = input.resolved
        val ph = input.physics
        val face = input.face
        val p = params
        p.tier = r.tier
        p.blurPx = r.platter.blurRadius.toPx()
        p.saturation = r.vibrancy
        p.refractPx = r.platter.refractionAmount.toPx()
        p.refractHeightPx = r.platter.refractionHeight.toPx()
        p.frostDepthPx = GlassConstants.FrostFullDepth.dp.toPx()
        p.setFace(face)
        p.tint = r.tintColor
        p.tintIntensity = if (r.hasTint) r.tintIntensity else 0f
        p.lightAngleRad = Math.toRadians(input.lightAngleDeg.toDouble()).toFloat()
        p.rimWidthPx = r.rimWidth.toPx()
        p.rimIntensity = r.specularIntensity
        p.specular = 0.35f * r.specularIntensity
        p.specularFalloff = r.specularFalloff
        p.rimMatrix = if (input.darkGlass) GlassConstants.PlatterRimDark else GlassConstants.PlatterRimLight
        p.press = ph.press.value.coerceIn(0f, 1.2f)
        p.darkGlass = input.darkGlass
        p.contrastBorder = r.increaseContrast
        p.borderWidthPx = 1.dp.toPx()
        p.glowX = ph.fingerX + input.rowOffsetX
        p.glowY = ph.fingerY
        p.glowScale = ph.glowScale.value
        p.glowAlpha = if (r.reduceTransparency) 0f else GlassConstants.GlowOpacity * ph.press.value.coerceIn(0f, 1f)
        p.glowRadiusPx = GlassConstants.GlowDiameter.dp.toPx()
        p.shadowAlpha = r.shadow.alpha
        p.shadowRadiusPx = r.shadow.radius.toPx()
        p.shadowOffsetPx = r.shadow.offsetY.toPx()
    }

    private fun fillLensUniforms(scope: DrawScope, input: TabBarDrawInput, lift: Float, alpha: Float) = with(scope) {
        val r = input.resolved
        val u = lensUniforms
        val angle = Math.toRadians(input.lightAngleDeg.toDouble())
        val f = lensFrame
        u.cx = lensRect.cx - f.left
        u.cy = lensRect.cy - f.top
        u.hw = lensRect.hw
        u.hh = lensRect.hh
        u.radius = lensRect.r
        // Keep samples on real backdrop pixels when the lens frame pokes past the backdrop.
        val backdropLeft = if (src.isAvailable) -src.offsetX - f.left else 0f
        val backdropTop = if (src.isAvailable) -src.offsetY - f.top else 0f
        u.clampLeft = max(0f, backdropLeft) + 0.5f
        u.clampTop = max(0f, backdropTop) + 0.5f
        u.clampRight = (if (src.isAvailable) min(f.width.toFloat(), backdropLeft + src.width) else f.width.toFloat()) - 0.5f
        u.clampBottom = (if (src.isAvailable) min(f.height.toFloat(), backdropTop + src.height) else f.height.toFloat()) - 0.5f
        u.oval = GlassConstants.Ovalization
        u.refract = r.lens.refractionAmount.toPx()
        u.refractHeight = max(r.lens.refractionHeight.toPx(), 1f)
        u.warp = r.lens.warpAmount.toPx() * lift.coerceIn(0f, 1f)
        u.warpHeight = max(r.lens.warpHeight.toPx(), 1f)
        u.aberration = r.lens.chromaticAberration * density
        u.aberrationDepth = GlassConstants.AberrationDepth.dp.toPx()
        u.tone = GlassConstants.LensTone
        u.topShade = GlassConstants.LensTopShade
        u.topShadeDepth = GlassConstants.LensTopShadeDepth.dp.toPx()
        when {
            r.increaseContrast -> {
                u.faceGray = if (input.darkGlass) 0f else 1f
                u.faceAlpha = 0.6f
            }
            r.reduceTransparency -> {
                u.faceGray = input.face.flatGray()
                u.faceAlpha = 0.7f
            }
            r.tinted -> {
                u.faceGray = input.face.flatGray()
                u.faceAlpha = 0.12f
            }
            else -> {
                u.faceGray = 1f
                u.faceAlpha = 0f
            }
        }
        u.lightX = cos(angle).toFloat()
        u.lightY = sin(angle).toFloat()
        u.rimWidth = max(r.rimWidth.toPx(), 0.5f)
        u.rimCurvature = GlassConstants.LensRimCurvature
        u.rimIntensity = r.specularIntensity
        u.specularFalloff = r.specularFalloff
        u.setRimMatrix(if (input.darkGlass) GlassConstants.LensRimDark else GlassConstants.LensRimLight)
        u.specular = 0.5f * r.specularIntensity
        u.press = input.physics.press.value.coerceIn(0f, 1.2f)
        u.alpha = alpha
    }

    private fun pillFilter(dark: Boolean): ColorFilter {
        val m = if (dark) GlassConstants.PillDark else GlassConstants.PillLight
        if (pillKey !== m || pillFilter == null) {
            pillKey = m
            pillFilter = ColorFilter.colorMatrix(ColorMatrix(GlassMaterial.vibrantMatrix(m, pillMatrix).copyOf()))
        }
        return pillFilter!!
    }
}
