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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Procedural, colorful content so the refraction of the glass is easy to see. No network. */
@Composable
fun DemoScreen(index: Int, bottomPadding: Dp, modifier: Modifier = Modifier) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottomPadding)
    when (index) {
        0 -> HomeScreen(padding, modifier)
        1 -> StatsScreen(padding, modifier)
        2 -> FriendsScreen(padding, modifier)
        3 -> SearchScreen(padding, modifier)
        else -> FavoritesScreen(padding, modifier)
    }
}

private val Palette = listOf(
    Color(0xFFFF5F6D), Color(0xFFFFC371), Color(0xFF47E5BC), Color(0xFF4C83FF),
    Color(0xFFB06AB3), Color(0xFFFF9A44), Color(0xFF00C9FF), Color(0xFF92FE9D),
    Color(0xFFF83600), Color(0xFF7F00FF), Color(0xFF00F260), Color(0xFFFC466B)
)

private fun gradient(i: Int) = Brush.linearGradient(listOf(Palette[i % Palette.size], Palette[(i + 3) % Palette.size]))

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Liquid Glass", "Press and drag across the tab bar") }
        item { HeroCard() }
        item { PatternCard("Stripes bend at the rim", Pattern.Stripes) }
        item { PatternCard("Checkerboard", Pattern.Checker) }
        item { ContrastCard(light = true) }
        item { ContrastCard(light = false) }
        item { PatternCard("Rings", Pattern.Rings) }
        items(18) { i -> PhotoCard(i) }
    }
}

@Composable
private fun HeroCard() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364), Color(0xFF00C9FF)))),
        contentAlignment = Alignment.BottomStart
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 7) {
                drawCircle(
                    Palette[i].copy(alpha = 0.55f),
                    radius = size.minDimension * (0.18f + 0.05f * i),
                    center = Offset(size.width * (0.15f + 0.13f * i), size.height * (0.3f + 0.08f * (i % 3)))
                )
            }
        }
        Column(Modifier.padding(20.dp)) {
            Text("iOS 26", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Lensing, not blur", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private enum class Pattern { Stripes, Checker, Rings }

@Composable
private fun PatternCard(title: String, pattern: Pattern) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            when (pattern) {
                Pattern.Stripes -> {
                    val w = 18.dp.toPx()
                    var x = 0f
                    var i = 0
                    while (x < size.width) {
                        drawRect(Palette[i % Palette.size], Offset(x, 0f), Size(w, size.height))
                        x += w
                        i++
                    }
                }
                Pattern.Checker -> {
                    val c = 22.dp.toPx()
                    var y = 0
                    while (y * c < size.height) {
                        var x = 0
                        while (x * c < size.width) {
                            if ((x + y) % 2 == 0) drawRect(Color(0xFF111111), Offset(x * c, y * c), Size(c, c))
                            else drawRect(Color(0xFFF5F5F5), Offset(x * c, y * c), Size(c, c))
                            x++
                        }
                        y++
                    }
                }
                Pattern.Rings -> {
                    drawRect(Color(0xFF101828))
                    for (i in 14 downTo 1) {
                        drawCircle(Palette[i % Palette.size], radius = i * 11.dp.toPx(), center = center, style = Stroke(5.dp.toPx()))
                    }
                }
            }
        }
        Text(title, Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContrastCard(light: Boolean) {
    val bg = if (light) Color.White else Color.Black
    val fg = if (light) Color.Black else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(20.dp)
    ) {
        Text(if (light) "Light content" else "Dark content", color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scroll this card behind the tab bar: the glass samples the content underneath and flips " +
                "between light and dark with the same hysteresis UIKit uses.",
            color = fg.copy(alpha = 0.7f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun PhotoCard(i: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient(i)),
        contentAlignment = Alignment.BottomStart
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path()
            path.moveTo(0f, size.height * 0.7f)
            var x = 0f
            while (x <= size.width) {
                path.lineTo(x, size.height * (0.62f + 0.12f * sin(x / size.width * 6.283f * (1 + i % 3) + i)))
                x += 8f
            }
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            drawPath(path, Color.Black.copy(alpha = 0.25f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 26.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.28f))
        }
        Text(
            "Collection ${i + 1}",
            Modifier.padding(18.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatsScreen(padding: PaddingValues, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Stats", "Charts behind glass") }
        items(10) { i ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(18.dp)
            ) {
                Text(listOf("Steps", "Energy", "Sleep", "Focus", "Water")[i % 5], fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(i + 3) * 1234}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette[i % Palette.size])
                Spacer(Modifier.height(10.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    if (i % 2 == 0) {
                        val n = 12
                        val bw = size.width / (n * 1.6f)
                        for (b in 0 until n) {
                            val h = size.height * (0.25f + 0.7f * ((sin(b * 1.3f + i) + 1f) / 2f))
                            drawRoundRect(
                                Palette[(b + i) % Palette.size],
                                topLeft = Offset(b * bw * 1.6f, size.height - h),
                                size = Size(bw, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f)
                            )
                        }
                    } else {
                        val path = Path()
                        for (s in 0..40) {
                            val x = size.width * s / 40f
                            val y = size.height * (0.5f - 0.4f * sin(s * 0.35f + i))
                            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, Palette[i % Palette.size], style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

private val Names = listOf(
    "Ava", "Liam", "Mia", "Noah", "Zoe", "Ethan", "Ivy", "Lucas", "Nora", "Leo", "Ella", "Mason",
    "Ruby", "Owen", "Luna", "Jack", "Iris", "Theo", "Maya", "Finn"
)

@Composable
private fun FriendsScreen(padding: PaddingValues, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Friends", "${Names.size} people") }
        items(Names.size) { i ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(gradient(i)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Names[i].take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(Names[i], fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Shared ${i + 2} photos with you", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(padding: PaddingValues, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) { ScreenTitle("Search", "Browse categories") }
        item(span = { GridItemSpan(2) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(DemoIcons.Search, null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }
        val categories = listOf(
            "Music", "Travel", "Food", "Art", "Sport", "Nature", "Tech", "Books", "Film", "Games", "Design", "Space",
            "Fashion", "Cars", "Pets", "Science", "Health", "Comedy", "History", "Ocean", "Cities", "Gardens", "Coffee", "Dance"
        )
        items(categories.filter { it.contains(query, ignoreCase = true) }) { name ->
            val i = categories.indexOf(name)
            Box(
                Modifier
                    .aspectRatio(1.3f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(gradient(i)),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(name, Modifier.padding(14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FavoritesScreen(padding: PaddingValues, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Favorites", "Things you love") }
        items(12) { i -> PhotoCard(i + 5) }
    }
}
