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

import android.os.SystemClock
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Tab bar haptics: a light tick per tab crossed (rate limited) and a confirmation on selection. */
internal class GlassHaptics {
    var feedback: HapticFeedback? = null
    var enabled = true
    private var lastTick = 0L

    fun tick() {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 50L) return
        lastTick = now
        feedback?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    fun confirm() {
        if (!enabled) return
        feedback?.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}
