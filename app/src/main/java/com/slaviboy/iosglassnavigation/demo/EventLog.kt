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

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** The last tab bar listener calls, newest first. */
@Stable
class EventLogState(private val capacity: Int = 50) {
    val events = mutableStateListOf<String>()
    private var lastDrag = 0L

    fun add(event: String) {
        events.add(0, event)
        if (events.size > capacity) events.removeAt(events.lastIndex)
    }

    /** onDrag fires every frame; keep the log readable. */
    fun addDrag(fraction: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDrag < 100L) return
        lastDrag = now
        add("onDrag(%.2f)".format(if (abs(fraction) < 0.005f) 0f else fraction))
    }
}

@Composable
fun EventLog(state: EventLogState, contentColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Listener log", color = contentColor.copy(alpha = 0.6f), fontSize = 12.sp)
        val lines = state.events.take(3)
        if (lines.isEmpty()) {
            Text("Press and drag the tab bar", color = contentColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        lines.forEachIndexed { i, line ->
            Text(
                line,
                Modifier.fillMaxWidth(),
                color = contentColor.copy(alpha = 1f - i * 0.25f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
