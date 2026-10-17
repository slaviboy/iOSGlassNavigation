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

package com.slaviboy.iosglassnavigation.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slaviboy.liquidglass.config.GlassAppearance
import com.slaviboy.liquidglass.config.GlassColorScheme
import com.slaviboy.liquidglass.config.GlassVariant
import com.slaviboy.liquidglass.config.LiquidGlassSettings
import com.slaviboy.liquidglass.config.LiquidGlassStyle
import com.slaviboy.liquidglass.config.RenderTier
import com.slaviboy.liquidglass.tabbar.LiquidGlassTabBarState
import com.slaviboy.liquidglass.tabbar.TabBarMinimizeBehavior

/** Live controls for every setting and style knob of the library. */
@Composable
fun SettingsPanel(
    config: DemoConfig,
    onConfigChange: (DemoConfig) -> Unit,
    barState: LiquidGlassTabBarState,
    modifier: Modifier = Modifier
) {
    val s = config.settings
    val st = config.style
    fun settings(block: LiquidGlassSettings.() -> LiquidGlassSettings) = onConfigChange(config.copy(settings = s.block()))
    fun style(block: LiquidGlassStyle.() -> LiquidGlassStyle) = onConfigChange(config.copy(style = st.block()))

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Section("Presets")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The library defaults: clear, dark glass that never minimizes.
            OutlinedButton(onClick = { onConfigChange(DemoConfig(showLog = config.showLog)) }) { Text("Clear dark") }
            OutlinedButton(onClick = {
                onConfigChange(
                    config.copy(
                        settings = LiquidGlassSettings(variant = GlassVariant.Regular, colorScheme = GlassColorScheme.Adaptive),
                        style = LiquidGlassStyle()
                    )
                )
            }) { Text("iOS default") }
            OutlinedButton(onClick = {
                onConfigChange(
                    config.copy(style = LiquidGlassStyle(tintColor = DemoConfig.ReferenceBlue, tintIntensity = 0.92f))
                )
            }) { Text("Reference blue") }
            OutlinedButton(onClick = {
                onConfigChange(config.copy(settings = s.copy(variant = GlassVariant.Clear)))
            }) { Text("Clear glass") }
            OutlinedButton(onClick = {
                onConfigChange(config.copy(style = st.copy(frost = 1.8f, platter = st.platter.copy(blurRadius = 24.dp))))
            }) { Text("Frosted") }
        }

        Section("Liquid Glass (iOS settings)")
        Choice("Variant", GlassVariant.entries, s.variant) { settings { copy(variant = it) } }
        Choice("Appearance", GlassAppearance.entries, s.appearance) { settings { copy(appearance = it) } }
        Choice("Color scheme", GlassColorScheme.entries, s.colorScheme) { settings { copy(colorScheme = it) } }
        Choice("Render tier", RenderTier.entries, s.renderTier) { settings { copy(renderTier = it) } }

        Section("Accessibility")
        Toggle("Reduce Transparency", s.accessibility.reduceTransparency) {
            settings { copy(accessibility = accessibility.copy(reduceTransparency = it)) }
        }
        Toggle("Increase Contrast", s.accessibility.increaseContrast) {
            settings { copy(accessibility = accessibility.copy(increaseContrast = it)) }
        }
        Toggle("Reduce Motion", s.accessibility.reduceMotion) {
            settings { copy(accessibility = accessibility.copy(reduceMotion = it)) }
        }
        Toggle("Follow system Reduce Motion", s.followSystem.reduceMotion) {
            settings { copy(followSystem = followSystem.copy(reduceMotion = it)) }
        }
        Toggle("Follow system contrast", s.followSystem.increaseContrast) {
            settings { copy(followSystem = followSystem.copy(increaseContrast = it)) }
        }
        Toggle("No window blur = Reduce Transparency", s.followSystem.reduceTransparency) {
            settings { copy(followSystem = followSystem.copy(reduceTransparency = it)) }
        }

        Section("Bar optics")
        DpSlider("Blur", st.platter.blurRadius, 0f, 40f) { style { copy(platter = platter.copy(blurRadius = it)) } }
        FloatSlider("Frost", st.frost, 0f, 2f) { style { copy(frost = it) } }
        DpSlider("Refraction", st.platter.refractionAmount, 0f, 80f) { style { copy(platter = platter.copy(refractionAmount = it)) } }
        DpSlider("Bezel width", st.platter.refractionHeight, 1f, 40f) { style { copy(platter = platter.copy(refractionHeight = it)) } }
        FloatSlider("Vibrancy", st.vibrancy, 0.5f, 3f) { style { copy(vibrancy = it) } }

        Section("Lens optics")
        DpSlider("Inner refraction", st.lens.refractionAmount, 0f, 40f) { style { copy(lens = lens.copy(refractionAmount = it)) } }
        DpSlider("Inner bezel", st.lens.refractionHeight, 1f, 30f) { style { copy(lens = lens.copy(refractionHeight = it)) } }
        DpSlider("Warp", st.lens.warpAmount, 0f, 24f) { style { copy(lens = lens.copy(warpAmount = it)) } }
        DpSlider("Warp reach", st.lens.warpHeight, 1f, 60f) { style { copy(lens = lens.copy(warpHeight = it)) } }
        FloatSlider("Chromatic aberration", st.lens.chromaticAberration, 0f, 4f) { style { copy(lens = lens.copy(chromaticAberration = it)) } }
        DpSlider("Lens blur", st.lens.blurRadius, 0f, 20f) { style { copy(lens = lens.copy(blurRadius = it)) } }
        FloatSlider("Magnification", st.lensMagnification, 1f, 1.6f) { style { copy(lensMagnification = it) } }

        Section("Light")
        FloatSlider("Specular intensity", st.specularIntensity, 0f, 2f) { style { copy(specularIntensity = it) } }
        FloatSlider("Light angle", st.specularAngle, 0f, 180f, decimals = 0) { style { copy(specularAngle = it) } }
        FloatSlider("Falloff", st.specularFalloff, 0.5f, 8f) { style { copy(specularFalloff = it) } }
        DpSlider("Rim width", st.rimWidth, 0f, 4f) { style { copy(rimWidth = it) } }
        Toggle("Rotate highlight with device tilt", s.deviceTiltHighlight) { settings { copy(deviceTiltHighlight = it) } }

        Section("Color")
        Text("Tint", Modifier.padding(top = 4.dp), fontSize = 14.sp)
        ColorChips(
            colors = listOf(Color.Unspecified, DemoConfig.ReferenceBlue, Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFAF52DE), Color(0xFFFF9500), Color(0xFF1C1C1E), Color.White),
            selected = st.tintColor
        ) { style { copy(tintColor = it) } }
        FloatSlider("Tint intensity", st.tintIntensity, 0f, 1f) { style { copy(tintIntensity = it) } }
        Text("Accent", Modifier.padding(top = 4.dp), fontSize = 14.sp)
        ColorChips(
            colors = listOf(Color.Unspecified, Color(0xFFFF2D55), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFF5856D6)),
            selected = st.accentColor
        ) { style { copy(accentColor = it) } }
        FloatSlider("Shadow", st.shadow.alpha, 0f, 0.5f) { style { copy(shadow = shadow.copy(alpha = it)) } }

        Section("Behavior")
        Toggle("Haptics", s.haptics) { settings { copy(haptics = it) } }
        Toggle("Badges", config.badges) { onConfigChange(config.copy(badges = it)) }
        Toggle("Listener log", config.showLog) { onConfigChange(config.copy(showLog = it)) }
        Choice("Minimize on scroll", TabBarMinimizeBehavior.entries, config.minimizeBehavior) {
            onConfigChange(config.copy(minimizeBehavior = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { barState.minimize() }) { Text("Minimize") }
            OutlinedButton(onClick = { barState.expand() }) { Text("Expand") }
        }
        Choice("Tabs", listOf(3, 4, 5), config.tabCount) { onConfigChange(config.copy(tabCount = it)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onConfigChange(DemoConfig()) }, Modifier.fillMaxWidth()) { Text("Reset everything") }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(top = 18.dp, bottom = 6.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit) {
    Text(label, Modifier.padding(top = 6.dp, bottom = 4.dp), fontSize = 14.sp)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {}
            ) {
                Text(option.toString(), fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FloatSlider(label: String, value: Float, min: Float, max: Float, decimals: Int = 2, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 2.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text("%.${decimals}f".format(value), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun DpSlider(label: String, value: Dp, min: Float, max: Float, onChange: (Dp) -> Unit) {
    Column(Modifier.padding(top = 2.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text("%.1f dp".format(value.value), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Slider(value = value.value.coerceIn(min, max), onValueChange = { onChange(it.dp) }, valueRange = min..max)
    }
}

@Composable
private fun ColorChips(colors: List<Color>, selected: Color, onSelect: (Color) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (color.isSpecified) color else Color.Transparent)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center
            ) {
                if (!color.isSpecified) Text("auto", fontSize = 9.sp)
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
