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

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.program.ShaderProgram
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Utility class for OpenGL ES.
 */
object GLUtils {
    /** Unknown version information.  */
    const val VERSION_UNKNOWN: String = "0.0"

    const val TAG: String = "GLUtils"

    const val EGL_GL_COLORSPACE_KHR: Int = 0x309D
    const val EGL_GL_COLORSPACE_BT2020_HLG_EXT: Int = 0x3540

    const val VAR_TEXTURE_COORD: String = "vTextureCoord"
    const val VAR_TEXTURE: String = "sTexture"
    const val PIXEL_STRIDE: Int = 4
    val EMPTY_ATTRIBS: IntArray = intArrayOf(EGL14.EGL_NONE)
    val HLG_SURFACE_ATTRIBS: IntArray = intArrayOf(
        EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_HLG_EXT,
        EGL14.EGL_NONE
    )

    val VERTEX_COORDS: FloatArray = floatArrayOf(
        -1.0f, -1.0f,  // 0 bottom left
        1.0f, -1.0f,  // 1 bottom right
        -1.0f, 1.0f,  // 2 top left
        1.0f, 1.0f,  // 3 top right
    )
    val VERTEX_BUF: FloatBuffer = createFloatBuffer(VERTEX_COORDS)

    val TEX_COORDS: FloatArray = floatArrayOf(
        0.0f, 0.0f,  // 0 bottom left
        1.0f, 0.0f,  // 1 bottom right
        0.0f, 1.0f,  // 2 top left
        1.0f, 1.0f // 3 top right
    )
    val TEX_BUF: FloatBuffer = createFloatBuffer(TEX_COORDS)

    const val SIZEOF_FLOAT: Int = 4
    val NO_OUTPUT_SURFACE: OutputSurface = OutputSurface.of(EGL14.EGL_NO_SURFACE, 0, 0)


    /**
     * Creates an [EGLSurface].
     */
    fun createWindowSurface(
        eglDisplay: EGLDisplay,
        eglConfig: EGLConfig, surface: Surface, surfaceAttrib: IntArray
    ): EGLSurface {
        // Create a window surface, and attach it to the Surface we received.
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surface,
            surfaceAttrib,  /*offset=*/0
        )
        checkEglErrorOrThrow("eglCreateWindowSurface")
        checkNotNull(eglSurface) { "surface was null" }
        return eglSurface
    }

    /**
     * Queries the [EGLSurface] information.
     */
    fun querySurface(
        eglDisplay: EGLDisplay, eglSurface: EGLSurface,
        what: Int
    ): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, what, value,  /*offset=*/0)
        return value[0]
    }

    /**
     * Gets the size of [EGLSurface].
     */
    fun getSurfaceSize(
        eglDisplay: EGLDisplay,
        eglSurface: EGLSurface
    ): Size {
        val width = querySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH)
        val height = querySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT)
        return Size(width, height)
    }

    /**
     * Creates a [FloatBuffer].
     */
    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(coords.size * SIZEOF_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }

    /**
     * Creates a new EGL pixel buffer surface.
     */
    fun createPBufferSurface(
        eglDisplay: EGLDisplay,
        eglConfig: EGLConfig, width: Int, height: Int
    ): EGLSurface {
        val surfaceAttrib = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay, eglConfig, surfaceAttrib,  /*offset=*/
            0
        )
        checkEglErrorOrThrow("eglCreatePbufferSurface")
        checkNotNull(eglSurface) { "surface was null" }
        return eglSurface
    }

    fun createPipelines(
        dynamicRange: DynamicRange,
    ): Map<InputFormat, ShaderProgram> {
        return InputFormat.entries
            .associate { inputFormat ->
                // TODO: implement out input formats
                val pipeline = when {
                    inputFormat == InputFormat.DEFAULT -> ShaderProgram()
                    inputFormat == InputFormat.YUV -> ShaderProgram()
                    inputFormat == InputFormat.UNKNOWN -> ShaderProgram()
                    dynamicRange.is10BitHdrBackport -> ShaderProgram()
                    else -> ShaderProgram()
                }
                inputFormat to pipeline
            }
    }

    /**
     * Creates a texture.
     */
    fun createTexture(): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        checkGlErrorOrThrow("glGenTextures")

        val texId = textures[0]
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlErrorOrThrow("glBindTexture $texId")

        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST)
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
        checkGlErrorOrThrow("glTexParameter")
        return texId
    }

    /**
     * Creates a 4x4 identity matrix.
     */
    fun create4x4IdentityMatrix(): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix,  /* smOffset= */0)
        return matrix
    }

    /**
     * Checks the location error.
     */
    fun checkLocationOrThrow(location: Int, label: String) {
        check(location >= 0) { "Unable to locate '$label' in program" }
    }

    /**
     * Checks the egl error and throw.
     */
    fun checkEglErrorOrThrow(op: String) {
        val error = EGL14.eglGetError()
        check(error == EGL14.EGL_SUCCESS) { "$op: EGL error: 0x${Integer.toHexString(error)}" }
    }

    /**
     * Checks the gl error and throw.
     */
    fun checkGlErrorOrThrow(op: String) {
        val error = GLES31.glGetError()
        check(error == GLES31.GL_NO_ERROR) { "$op: GL error 0x${Integer.toHexString(error)}" }
    }

    /**
     * Checks the egl error and log.
     */
    fun checkEglErrorOrLog(op: String) {
        try {
            checkEglErrorOrThrow(op)
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.toString(), e)
        }
    }

    /**
     * Checks the initialization status.
     */
    fun checkInitializedOrThrow(
        initialized: AtomicBoolean,
        shouldInitialized: Boolean
    ) {
        val result = shouldInitialized == initialized.get()
        val message = if (shouldInitialized)
            "OpenGlRenderer is not initialized"
        else
            "OpenGlRenderer is already initialized"
        check(result) { message }
    }

    /**
     * Checks the gl thread.
     */
    fun checkGlThreadOrThrow(thread: Thread?) {
        check(
            thread === Thread.currentThread()
        ) { "Method call must be called on the GL thread." }
    }

    /**
     * Gets the gl version number.
     */
    fun getGlVersionNumber(): String {
        // Logic adapted from CTS Egl14Utils:
        // https://cs.android.com/android/platform/superproject/+/master:cts/tests/tests/opengl/src/android/opengl/cts/Egl14Utils.java;l=46;drc=1c705168ab5118c42e5831cd84871d51ff5176d1
        val glVersion = GLES31.glGetString(GLES31.GL_VERSION)
        val pattern = Pattern.compile("OpenGL ES ([0-9]+)\\.([0-9]+).*")
        val matcher = pattern.matcher(glVersion)
        if (matcher.find()) {
            val major = checkNotNull(matcher.group(1))
            val minor = checkNotNull(matcher.group(2))
            return "$major.$minor"
        }
        return VERSION_UNKNOWN
    }

    /**
     * Chooses the surface attributes for HDR 10bit.
     */
    fun chooseSurfaceAttrib(
        eglExtensions: String,
        dynamicRange: DynamicRange
    ): IntArray {
        var attribs = EMPTY_ATTRIBS
        if (dynamicRange.encoding == DynamicRange.ENCODING_HLG) {
            if (eglExtensions.contains("EGL_EXT_gl_colorspace_bt2020_hlg")) {
                attribs = HLG_SURFACE_ATTRIBS
            } else {
                Log.w(
                    TAG,
                    "Dynamic range uses HLG encoding, but device does not support EGL_EXT_gl_colorspace_bt2020_hlg. Fallback to default colorspace."
                )
            }
        }
        // TODO(b/303675500): Add path for PQ (EGL_EXT_gl_colorspace_bt2020_pq) output for
        //  HDR10/HDR10+
        return attribs
    }

    /**
     * Generates framebuffer object.
     */
    fun generateFbo(): Int {
        val fbos = IntArray(1)
        GLES31.glGenFramebuffers(1, fbos, 0)
        checkGlErrorOrThrow("glGenFramebuffers")
        return fbos[0]
    }

    /**
     * Generates texture.
     */
    fun generateTexture(): Int {
        val textures = IntArray(1)
        GLES31.glGenTextures(1, textures, 0)
        checkGlErrorOrThrow("glGenTextures")
        return textures[0]
    }

    /**
     * Deletes texture.
     */
    fun deleteTexture(texture: Int) {
        val textures = intArrayOf(texture)
        GLES31.glDeleteTextures(1, textures, 0)
        checkGlErrorOrThrow("glDeleteTextures")
    }

    /**
     * Deletes framebuffer object.
     */
    fun deleteFbo(fbo: Int) {
        val fbos = intArrayOf(fbo)
        GLES31.glDeleteFramebuffers(1, fbos, 0)
        checkGlErrorOrThrow("glDeleteFramebuffers")
    }
}
