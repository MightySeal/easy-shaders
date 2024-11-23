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
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.ShaderProvider
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.HashMap
import java.util.Locale
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

    val DEFAULT_VERTEX_SHADER: String = String.format(
        Locale.US,
        ("uniform mat4 uTexMatrix;\n"
                + "uniform mat4 uTransMatrix;\n"
                + "attribute vec4 aPosition;\n"
                + "attribute vec4 aTextureCoord;\n"
                + "varying vec2 %s;\n"
                + "void main() {\n"
                + "    gl_Position = uTransMatrix * aPosition;\n"
                + "    %s = (uTexMatrix * aTextureCoord).xy;\n"
                + "}\n"), VAR_TEXTURE_COORD, VAR_TEXTURE_COORD
    )

    val HDR_VERTEX_SHADER: String = String.format(
        Locale.US,
        ("#version 300 es\n"
                + "in vec4 aPosition;\n"
                + "in vec4 aTextureCoord;\n"
                + "uniform mat4 uTexMatrix;\n"
                + "uniform mat4 uTransMatrix;\n"
                + "out vec2 %s;\n"
                + "void main() {\n"
                + "  gl_Position = uTransMatrix * aPosition;\n"
                + "  %s = (uTexMatrix * aTextureCoord).xy;\n"
                + "}\n"), VAR_TEXTURE_COORD, VAR_TEXTURE_COORD
    )

    val BLANK_VERTEX_SHADER: String = ("uniform mat4 uTransMatrix;\n"
            + "attribute vec4 aPosition;\n"
            + "void main() {\n"
            + "    gl_Position = uTransMatrix * aPosition;\n"
            + "}\n")

    val BLANK_FRAGMENT_SHADER: String = ("precision mediump float;\n"
            + "uniform float uAlphaScale;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(0.0, 0.0, 0.0, uAlphaScale);\n"
            + "}\n")

    val SHADER_PROVIDER_DEFAULT: ShaderProvider = object : ShaderProvider {
        override fun createFragmentShader(
            samplerVarName: String,
            fragCoordsVarName: String
        ): String {
            return String.format(
                Locale.US,
                ("#extension GL_OES_EGL_image_external : require\n"
                        + "precision mediump float;\n"
                        + "varying vec2 %s;\n"
                        + "uniform samplerExternalOES %s;\n"
                        + "uniform float uAlphaScale;\n"
                        + "void main() {\n"
                        + "    vec4 src = texture2D(%s, %s);\n"
                        + "    gl_FragColor = vec4(src.rgb, src.a * uAlphaScale);\n"
                        + "}\n"),
                fragCoordsVarName, samplerVarName, samplerVarName, fragCoordsVarName
            )
        }
    }

    val SHADER_PROVIDER_HDR_DEFAULT: ShaderProvider = object : ShaderProvider {
        override fun createFragmentShader(
            samplerVarName: String,
            fragCoordsVarName: String
        ): String {
            return String.format(
                Locale.US,
                ("#version 300 es\n"
                        + "#extension GL_OES_EGL_image_external_essl3 : require\n"
                        + "precision mediump float;\n"
                        + "uniform samplerExternalOES %s;\n"
                        + "uniform float uAlphaScale;\n"
                        + "in vec2 %s;\n"
                        + "out vec4 outColor;\n"
                        + "\n"
                        + "void main() {\n"
                        + "  vec4 src = texture(%s, %s);\n"
                        + "  outColor = vec4(src.rgb, src.a * uAlphaScale);\n"
                        + "}"),
                samplerVarName, fragCoordsVarName, samplerVarName, fragCoordsVarName
            )
        }
    }

    val SHADER_PROVIDER_HDR_YUV: ShaderProvider = object : ShaderProvider {
        override fun createFragmentShader(
            samplerVarName: String,
            fragCoordsVarName: String
        ): String {
            return String.format(
                Locale.US,
                ("#version 300 es\n"
                        + "#extension GL_EXT_YUV_target : require\n"
                        + "precision mediump float;\n"
                        + "uniform __samplerExternal2DY2YEXT %s;\n"
                        + "uniform float uAlphaScale;\n"
                        + "in vec2 %s;\n"
                        + "out vec4 outColor;\n"
                        + "\n"
                        + "vec3 yuvToRgb(vec3 yuv) {\n"
                        + "  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);\n"
                        + "  const mat3 yuvToRgbColorMat = mat3(\n"
                        + "    1.1689f, 1.1689f, 1.1689f,\n"
                        + "    0.0000f, -0.1881f, 2.1502f,\n"
                        + "    1.6853f, -0.6530f, 0.0000f\n"
                        + "  );\n"
                        + "  return clamp(yuvToRgbColorMat * (yuv - yuvOffset), 0.0, 1.0);\n"
                        + "}\n"
                        + "\n"
                        + "void main() {\n"
                        + "  vec3 srcYuv = texture(%s, %s).xyz;\n"
                        + "  vec3 srcRgb = yuvToRgb(srcYuv);\n"
                        + "  outColor = vec4(srcRgb, uAlphaScale);\n"
                        + "}"),
                samplerVarName, fragCoordsVarName, samplerVarName, fragCoordsVarName
            )
        }
    }

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
     * Creates the vertex or fragment shader.
     */
    fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        checkGlErrorOrThrow("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled,  /*offset=*/0)
        if (compiled[0] == 0) {
            Log.w(TAG, "Could not compile shader: $source")
            val shaderLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException(
                "Could not compile shader type $shaderType:$shaderLog"
            )
        }
        return shader
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

    /**
     * Creates program objects based on shaders which are appropriate for each input format.
     *
     *
     * Each [InputFormat] may have different sampler requirements based on the dynamic
     * range. For that reason, we create a separate program for each input format, and will switch
     * to the program when the input format changes so we correctly sample the input texture
     * (or no-op, in some cases).
     */
    fun createPrograms(
        dynamicRange: DynamicRange,
        shaderProviderOverrides: MutableMap<InputFormat, ShaderProvider>
    ): MutableMap<InputFormat, Program2D> {
        val programs = HashMap<InputFormat, Program2D>()
        for (inputFormat in InputFormat.entries) {
            val shaderProviderOverride = shaderProviderOverrides.get(inputFormat)
            var program: Program2D?
            if (shaderProviderOverride != null) {
                // Always use the overridden shader provider if present
                program = SamplerShaderProgram(dynamicRange, shaderProviderOverride)
            } else if (inputFormat == InputFormat.YUV || inputFormat == InputFormat.DEFAULT) {
                // Use a default sampler shader for DEFAULT or YUV
                program = SamplerShaderProgram(dynamicRange, inputFormat)
            } else {
                check(
                    inputFormat == InputFormat.UNKNOWN,
                    { "Unhandled input format: $inputFormat" }
                )
                if (dynamicRange.is10BitHdrBackport) {
                    // InputFormat is UNKNOWN and we don't know if we need to use a
                    // YUV-specific sampler for HDR. Use a blank shader program.
                    program = BlankShaderProgram()
                } else {
                    // If we're not rendering HDR content, we can use the default sampler shader
                    // program since it can handle both YUV and DEFAULT inputs when the format
                    // is UNKNOWN.
                    val defaultShaderProviderOverride =
                        shaderProviderOverrides.get(InputFormat.DEFAULT)
                    if (defaultShaderProviderOverride != null) {
                        program = SamplerShaderProgram(
                            dynamicRange,
                            defaultShaderProviderOverride
                        )
                    } else {
                        program = SamplerShaderProgram(dynamicRange, InputFormat.DEFAULT)
                    }
                }
            }
            Log.d(
                TAG, ("Shader program for input format " + inputFormat + " created: "
                        + program)
            )
            programs.put(inputFormat, program)
        }
        return programs
    }

    /**
     * Creates a texture.
     */
    fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlErrorOrThrow("glGenTextures")

        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlErrorOrThrow("glBindTexture " + texId)

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
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
        check(location >= 0) { "Unable to locate '" + label + "' in program" }
    }

    /**
     * Checks the egl error and throw.
     */
    fun checkEglErrorOrThrow(op: String) {
        val error = EGL14.eglGetError()
        check(error == EGL14.EGL_SUCCESS) { op + ": EGL error: 0x" + Integer.toHexString(error) }
    }

    /**
     * Checks the gl error and throw.
     */
    fun checkGlErrorOrThrow(op: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { op + ": GL error 0x" + Integer.toHexString(error) }
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
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
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
                    TAG, ("Dynamic range uses HLG encoding, but "
                            + "device does not support EGL_EXT_gl_colorspace_bt2020_hlg."
                            + "Fallback to default colorspace.")
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
        GLES20.glGenFramebuffers(1, fbos, 0)
        checkGlErrorOrThrow("glGenFramebuffers")
        return fbos[0]
    }

    /**
     * Generates texture.
     */
    fun generateTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlErrorOrThrow("glGenTextures")
        return textures[0]
    }

    /**
     * Deletes texture.
     */
    fun deleteTexture(texture: Int) {
        val textures = intArrayOf(texture)
        GLES20.glDeleteTextures(1, textures, 0)
        checkGlErrorOrThrow("glDeleteTextures")
    }

    /**
     * Deletes framebuffer object.
     */
    fun deleteFbo(fbo: Int) {
        val fbos = intArrayOf(fbo)
        GLES20.glDeleteFramebuffers(1, fbos, 0)
        checkGlErrorOrThrow("glDeleteFramebuffers")
    }

    fun getFragmentShaderSource(shaderProvider: ShaderProvider): String {
        // Throw IllegalArgumentException if the shader provider can not provide a valid
        // fragment shader.
        try {
            val source = shaderProvider.createFragmentShader(VAR_TEXTURE, VAR_TEXTURE_COORD)
            // A simple check to workaround custom shader doesn't contain required variable.
            // See b/241193761.
            require(
                !(source == null || !source.contains(VAR_TEXTURE_COORD) || !source.contains(VAR_TEXTURE))
            ) { "Invalid fragment shader" }
            return source
        } catch (t: Throwable) {
            if (t is IllegalArgumentException) {
                throw t
            }
            throw IllegalArgumentException("Unable retrieve fragment shader source", t)
        }
    }
}
