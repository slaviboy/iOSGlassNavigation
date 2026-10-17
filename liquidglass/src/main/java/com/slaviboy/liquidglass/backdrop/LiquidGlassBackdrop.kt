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

package com.slaviboy.liquidglass.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

/**
 * The content that Liquid Glass refracts. Mark the content root with [liquidGlassBackdrop]; glass
 * elements drawn as later siblings (never descendants) of that root sample it.
 *
 * @param backgroundColor drawn under the content when recorded. Glass needs an opaque backdrop;
 *   set this when the content itself has a transparent background.
 */
@Stable
class LiquidGlassBackdrop internal constructor(val backgroundColor: Color) {

    internal var layer: GraphicsLayer? = null
    internal var coordinates: LayoutCoordinates? = null
    internal var isRecording = false

    /** Bumped when the backdrop moves or resizes, so glass re-reads its offset. */
    internal val version = mutableIntStateOf(0)

    internal val isAvailable: Boolean
        get() = layer != null && coordinates?.isAttached == true
}

@Composable
fun rememberLiquidGlassBackdrop(backgroundColor: Color = Color.Unspecified): LiquidGlassBackdrop =
    remember(backgroundColor) { LiquidGlassBackdrop(backgroundColor) }

/** Records this content so that [LiquidGlassBackdrop] glass elements can refract it. */
fun Modifier.liquidGlassBackdrop(backdrop: LiquidGlassBackdrop): Modifier =
    this then BackdropElement(backdrop)

private class BackdropElement(val backdrop: LiquidGlassBackdrop) : ModifierNodeElement<BackdropNode>() {
    override fun create() = BackdropNode(backdrop)

    override fun update(node: BackdropNode) {
        node.update(backdrop)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlassBackdrop"
    }

    override fun equals(other: Any?) = other is BackdropElement && other.backdrop === backdrop

    override fun hashCode() = backdrop.hashCode()
}

private class BackdropNode(var backdrop: LiquidGlassBackdrop) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var layer: GraphicsLayer? = null
    private var lastPosition = Offset.Unspecified
    private var lastSize = IntSize.Zero

    override fun onAttach() {
        val l = requireGraphicsContext().createGraphicsLayer()
        layer = l
        backdrop.layer = l
    }

    override fun onDetach() {
        layer?.let {
            if (backdrop.layer === it) backdrop.layer = null
            requireGraphicsContext().releaseGraphicsLayer(it)
        }
        layer = null
        backdrop.coordinates = null
    }

    fun update(newBackdrop: LiquidGlassBackdrop) {
        if (newBackdrop === backdrop) return
        if (backdrop.layer === layer) backdrop.layer = null
        backdrop.coordinates = null
        backdrop = newBackdrop
        backdrop.layer = layer
        lastPosition = Offset.Unspecified
    }

    override fun ContentDrawScope.draw() {
        val l = layer
        if (l == null) {
            drawContent()
            return
        }
        backdrop.isRecording = true
        try {
            l.record {
                val bg = backdrop.backgroundColor
                if (bg.isSpecified) drawRect(bg)
                this@draw.drawContent()
            }
        } finally {
            backdrop.isRecording = false
        }
        drawLayer(l)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.coordinates = coordinates
        val position = coordinates.positionInRoot()
        if (position != lastPosition || coordinates.size != lastSize) {
            lastPosition = position
            lastSize = coordinates.size
            backdrop.version.intValue++
        }
    }
}
