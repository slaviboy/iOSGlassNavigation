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

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slaviboy.liquidglass.effect.GlassProgram
import com.slaviboy.liquidglass.effect.GlassPrograms
import com.slaviboy.liquidglass.effect.ShaderUniforms
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AGSL is only compiled at runtime, so every program and uniform name is checked on a device. */
@RunWith(AndroidJUnit4::class)
class ShaderCompileTest {

    private fun uniforms() = ShaderUniforms().apply {
        cx = 200f
        cy = 60f
        hw = 180f
        hh = 31f
        radius = 31f
        clampRight = 400f
        clampBottom = 120f
        refract = 40f
        refractHeight = 15f
        warp = 9f
        warpHeight = 36f
        aberration = 2.6f
        aberrationDepth = 30f
        faceAlpha = 0.5f
        tone = 0.97f
        topShade = 0.06f
        topShadeDepth = 30f
    }

    @Test
    fun platterProgramCompilesAndAcceptsEveryUniform() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val program = GlassProgram(lens = false)
        assertNotNull(program.renderEffect(uniforms(), null))
    }

    @Test
    fun lensProgramCompilesAndAcceptsEveryUniform() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val program = GlassProgram(lens = true)
        assertNotNull(program.renderEffect(uniforms(), null))
    }

    @Test
    fun programsCompileTogether() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val programs = GlassPrograms.compile(withLens = true)
        assertNotNull(programs)
        assertNotNull(programs!!.lens)
    }
}
