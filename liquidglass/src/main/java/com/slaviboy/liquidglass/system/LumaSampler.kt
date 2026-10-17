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

package com.slaviboy.liquidglass.system

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.slaviboy.liquidglass.backdrop.LiquidGlassBackdrop
import com.slaviboy.liquidglass.config.GlassConstants
import com.slaviboy.liquidglass.glass.GlassMaterial
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the mean Rec.709 luma of the backdrop around a glass node, the way UIKit decides whether
 * a tab bar is light or dark: the node grown 21 dp to the sides and below and 12.4 dp above,
 * downscaled to a 24x6 image.
 *
 * On API 33+ the image is rendered by a persistent [HardwareRenderer] into an [ImageReader] and
 * read on a background thread, so sampling never stalls the UI or render thread. Older devices
 * fall back to a (slower) layer snapshot.
 */
internal class BackdropLumaSampler(private val graphicsContext: GraphicsContext) {

    private val reader: Any? = if (Build.VERSION.SDK_INT >= 33) runCatching { HardwareLumaReader() }.getOrNull() else null
    private val fallbackLayer: GraphicsLayer? = if (reader == null) graphicsContext.createGraphicsLayer() else null
    private val pixels = IntArray(WIDTH * HEIGHT)
    private var released = false

    fun release() {
        if (released) return
        released = true
        if (Build.VERSION.SDK_INT >= 33) (reader as? HardwareLumaReader)?.destroy()
        fallbackLayer?.let { graphicsContext.releaseGraphicsLayer(it) }
    }

    suspend fun sample(backdrop: LiquidGlassBackdrop, node: LayoutCoordinates?, density: Density): Float? {
        if (released || node == null || !node.isAttached) return null
        val backdropCoordinates = backdrop.coordinates ?: return null
        val backdropLayer = backdrop.layer ?: return null
        if (!backdropCoordinates.isAttached) return null

        val position = backdropCoordinates.localPositionOf(node, Offset.Zero)
        val side = GlassConstants.SampleGrowSides * density.density
        val above = GlassConstants.SampleGrowTop * density.density
        val left = max(0f, position.x - side)
        val top = max(0f, position.y - above)
        val right = min(backdropCoordinates.size.width.toFloat(), position.x + node.size.width + side)
        val bottom = min(backdropCoordinates.size.height.toFloat(), position.y + node.size.height + side)
        if (right - left < 2f || bottom - top < 2f) return null

        val draw: DrawScope.() -> Unit = {
            scale(WIDTH / (right - left), HEIGHT / (bottom - top), pivot = Offset.Zero) {
                translate(-left, -top) { drawLayer(backdropLayer) }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && reader is HardwareLumaReader) {
            return reader.sample(density, draw)
        }
        return sampleWithSnapshot(density, draw)
    }

    private suspend fun sampleWithSnapshot(density: Density, draw: DrawScope.() -> Unit): Float? {
        val layer = fallbackLayer ?: return null
        layer.record(density, LayoutDirection.Ltr, IntSize(WIDTH, HEIGHT), draw)
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val readable = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            bitmap
        }
        val w = min(WIDTH, readable.width)
        val h = min(HEIGHT, readable.height)
        if (w <= 0 || h <= 0) return null
        readable.getPixels(pixels, 0, WIDTH, 0, 0, w, h)
        if (readable !== bitmap) readable.recycle()
        var sum = 0f
        var count = 0
        for (i in 0 until w * h) {
            val c = pixels[(i / w) * WIDTH + i % w]
            if ((c ushr 24) and 0xFF == 0) continue
            sum += GlassMaterial.luma(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
            count++
        }
        return if (count == 0) null else sum / count
    }

    internal companion object {
        const val WIDTH = 24
        const val HEIGHT = 6
    }
}

/**
 * Renders a tiny snapshot on the render thread and reads it back on a background thread. The
 * image is read only after its acquire fence signals (API 33), so the GPU has finished writing it.
 */
@RequiresApi(33)
private class HardwareLumaReader {
    private val thread = HandlerThread("LiquidGlassLuma").apply { start() }
    private val handler = Handler(thread.looper)
    private val width = BackdropLumaSampler.WIDTH
    private val height = BackdropLumaSampler.HEIGHT
    private val reader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, 2,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    )
    private val root = RenderNode("LiquidGlassLuma")
    private val renderer = HardwareRenderer().apply {
        setSurface(reader.surface)
        setContentRoot(root)
    }
    private val drawScope = CanvasDrawScope()
    private val pixels = IntArray(width * height)

    @Volatile
    private var pending: CompletableDeferred<Float?>? = null

    init {
        root.setPosition(0, 0, width, height)
        reader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val luma = try {
                image.fence.use { fence -> if (fence.isValid) fence.await(Duration.ofMillis(250)) }
                readLuma(image.hardwareBuffer)
            } catch (t: Throwable) {
                null
            } finally {
                image.close()
            }
            pending?.complete(luma)
        }, handler)
    }

    /** GPU buffers are read through a hardware bitmap; direct CPU plane access is not portable. */
    private fun readLuma(buffer: HardwareBuffer?): Float? {
        buffer ?: return null
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)) ?: return null
            val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            hardware.recycle()
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()
        } finally {
            buffer.close()
        }
        var sum = 0f
        var count = 0
        for (c in pixels) {
            if ((c ushr 24) and 0xFF == 0) continue
            sum += GlassMaterial.luma(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
            count++
        }
        return if (count == 0) null else sum / count
    }

    suspend fun sample(density: Density, draw: DrawScope.() -> Unit): Float? {
        val canvas = root.beginRecording(width, height)
        try {
            drawScope.draw(density, LayoutDirection.Ltr, Canvas(canvas), Size(width.toFloat(), height.toFloat()), draw)
        } finally {
            root.endRecording()
        }
        val result = CompletableDeferred<Float?>()
        pending = result
        renderer.createRenderRequest().setWaitForPresent(false).syncAndDraw()
        return withTimeoutOrNull(500L) { result.await() }
    }

    fun destroy() {
        renderer.destroy()
        reader.close()
        thread.quitSafely()
    }
}
