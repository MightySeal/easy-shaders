/*
 * Copyright 2024 The Android Open Source Project
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
package io.easyshaders.lib.processing.util

import android.opengl.EGLSurface
// import androidx.camera.core.processing.util.AutoValue_OutputSurface
import com.google.auto.value.AutoValue

/**
 * Wrapper for output [EGLSurface] in [androidx.camera.core.processing.OpenGlRenderer].
 */
@AutoValue
abstract class OutputSurface {
    /**
     * Gets [EGLSurface].
     */
    abstract fun getEglSurface(): EGLSurface

    /**
     * Gets [EGLSurface] width.
     */
    abstract fun getWidth(): Int

    /**
     * Gets [EGLSurface] height.
     */
    abstract fun getHeight(): Int

    companion object {
        /**
         * Creates [OutputSurface].
         */
        fun of(eglSurface: EGLSurface, width: Int, height: Int): OutputSurface {
            return AutoValue_OutputSurface(eglSurface, width, height)
        }
    }
}
