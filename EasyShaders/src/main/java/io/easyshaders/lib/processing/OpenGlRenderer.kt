/*
 * Copyright 2022 The Android Open Source Project
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
package io.easyshaders.lib.processing

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import androidx.annotation.WorkerThread
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.util.InputFormat
import io.easyshaders.lib.processing.util.GLUtils
import io.easyshaders.lib.processing.util.GraphicDeviceInfo
import io.easyshaders.lib.processing.util.OutputSurface
import io.easyshaders.lib.processing.util.Program2D
import io.easyshaders.lib.processing.util.SamplerShaderProgram
import io.easyshaders.lib.processing.util.is10BitHdrBackport
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGL10

/**
 * OpenGLRenderer renders texture image to the output surface.
 *
 *
 * OpenGLRenderer's methods must run on the same thread, so called GL thread. The GL thread is
 * locked as the thread running the [.init] method, otherwise an
 * [IllegalStateException] will be thrown when other methods are called.
 */
@WorkerThread
class OpenGlRenderer {
    protected val isInitialized: AtomicBoolean = AtomicBoolean(false)
    protected val outputSurfaceMap: MutableMap<Surface, OutputSurface> = mutableMapOf()
    protected var glThread: Thread? = null
    protected var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    protected var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    // protected var mSurfaceAttrib: IntArray = intArrayOf(EGL14.EGL_NONE)
    protected var surfaceAttrib: IntArray = GLUtils.EMPTY_ATTRIBS
    protected var eglConfig: EGLConfig? = null
    protected var tempSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    protected var currentSurface: Surface? = null
    protected var programHandles: MutableMap<InputFormat, Program2D> = mutableMapOf()
    protected var currentProgram: Program2D? = null
    protected var currentInputformat: InputFormat = InputFormat.UNKNOWN

    private var externalTextureId = -1


    private lateinit var initDynamicRange: DynamicRange
    /**
     * Initializes the OpenGLRenderer
     *
     *
     * Initialization must be done before calling other methods, otherwise an
     * [IllegalStateException] will be thrown. Following methods must run on the same
     * thread as this method, so called GL thread, otherwise an [IllegalStateException]
     * will be thrown.
     *
     * @param dynamicRange    the dynamic range used to select default shaders.
     * @param shaderOverrides specific shader overrides for fragment shaders
     * per [InputFormat].
     * @return Info about the initialized graphics device.
     * @throws IllegalStateException    if the renderer is already initialized or failed to be
     * initialized.
     * @throws IllegalArgumentException if the ShaderProvider fails to create shader or provides
     * invalid shader string.
     */
    /**
     * Initializes the OpenGLRenderer
     *
     *
     * This is equivalent to calling [.init] without providing any
     * shader overrides. Default shaders will be used for the dynamic range specified.
     */
    @JvmOverloads
    fun init(
        dynamicRange: DynamicRange,
        shaderOverrides: MutableMap<InputFormat, ShaderProvider> = mutableMapOf<InputFormat, ShaderProvider>()
    ): GraphicDeviceInfo {
        this.initDynamicRange = dynamicRange
        var dynamicRange = dynamicRange
        GLUtils.checkInitializedOrThrow(isInitialized, false)
        val infoBuilder = GraphicDeviceInfo.Builder()
        try {
            if (dynamicRange.is10BitHdrBackport) {
                val extensions = getExtensionsBeforeInitialized(dynamicRange)
                val (glExtensions, eglExtensions) = extensions

                if (!glExtensions.contains("GL_EXT_YUV_target")) {
                    Log.w(TAG, "Device does not support GL_EXT_YUV_target. Fallback to SDR.")
                    dynamicRange = DynamicRange.SDR
                }
                surfaceAttrib = GLUtils.chooseSurfaceAttrib(eglExtensions, dynamicRange)
                infoBuilder.setGlExtensions(glExtensions)
                infoBuilder.setEglExtensions(eglExtensions)
            }
            createEglContext(dynamicRange, infoBuilder)
            createTempSurface()
            makeCurrent(tempSurface)
            infoBuilder.setGlVersion(GLUtils.getGlVersionNumber())
            programHandles = GLUtils.createPrograms(dynamicRange, shaderOverrides)
            externalTextureId = GLUtils.createTexture()
            useAndConfigureProgramWithTexture(externalTextureId)
        } catch (e: IllegalStateException) {
            releaseInternal()
            throw e
        } catch (e: IllegalArgumentException) {
            releaseInternal()
            throw e
        }
        glThread = Thread.currentThread()
        isInitialized.set(true)
        return infoBuilder.build()
    }

    fun setFragmentShader(dynamicRange: DynamicRange) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        val currentPrograms = HashMap(programHandles)
        // TODO: dispose old programs?

        val provider =  object : ShaderProvider {
            override fun createFragmentShader(
                samplerVarName: String,
                fragCoordsVarName: String
            ): String = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 $fragCoordsVarName;
                uniform samplerExternalOES $samplerVarName;
                uniform float uAlphaScale;
                
                vec3 grayscale(vec3 color) {
                    return vec3(color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722);
                }
                
                void main() {
                    vec4 src = texture2D($samplerVarName, $fragCoordsVarName);
                    vec3 grayscale = grayscale(src.rgb);
                    
                    gl_FragColor = vec4(grayscale, src.a * uAlphaScale);
                }
            """.trimIndent().also {
                println("========== Create override program!")
            }
        }

        val newPrograms = GLUtils.createPrograms(
            dynamicRange,
            mapOf(InputFormat.DEFAULT to provider)
        )

        programHandles = newPrograms
        useAndConfigureProgramWithTexture(textureId = externalTextureId)

        // TODO: dispose old ones


    }

    /**
     * Releases the OpenGLRenderer
     *
     * @throws IllegalStateException if the caller doesn't run on the GL thread.
     */
    fun release() {
        if (!isInitialized.getAndSet(false)) {
            return
        }
        GLUtils.checkGlThreadOrThrow(glThread)
        releaseInternal()
    }

    /**
     * Register the output surface.
     *
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun registerOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        if (!outputSurfaceMap.containsKey(surface)) {
            outputSurfaceMap.put(surface, GLUtils.NO_OUTPUT_SURFACE)
        }
    }

    /**
     * Unregister the output surface.
     *
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun unregisterOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        removeOutputSurfaceInternal(surface, true)
    }

    /**
     * Gets the texture name.
     *
     * @return the texture name
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun getTextureName(): Int {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        return externalTextureId
    }

    /**
     * Sets the input format.
     *
     *
     * This will ensure the correct sampler is used for the input.
     *
     * @param inputFormat The input format for the input texture.
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun setInputFormat(inputFormat: InputFormat) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        if (currentInputformat != inputFormat) {
            currentInputformat = inputFormat
            useAndConfigureProgramWithTexture(externalTextureId)
        }
    }

    private fun activateExternalTexture(externalTextureId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLUtils.checkGlErrorOrThrow("glActiveTexture")

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLUtils.checkGlErrorOrThrow("glBindTexture")
    }

    /**
     * Renders the texture image to the output surface.
     *
     * @throws IllegalStateException if the renderer is not initialized, the caller doesn't run
     * on the GL thread or the surface is not registered by
     * [.registerOutputSurface].
     */
    fun render(
        timestampNs: Long, textureTransform: FloatArray,
        surface: Surface
    ) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        var outputSurface: OutputSurface? = getOutSurfaceOrThrow(surface)

        // Workaround situations that out surface is failed to create or needs to be recreated.
        if (outputSurface === GLUtils.NO_OUTPUT_SURFACE) {
            outputSurface = createOutputSurfaceInternal(surface)
            if (outputSurface == null) {
                return
            }

            outputSurfaceMap.put(surface, outputSurface)
        }

        // Set output surface.
        if (surface !== currentSurface) {
            makeCurrent(outputSurface!!.eglSurface)
            currentSurface = surface
            GLES20.glViewport(0, 0, outputSurface.width, outputSurface.height)
            GLES20.glScissor(0, 0, outputSurface.width, outputSurface.height)
        }

        // TODO(b/245855601): Upload the matrix to GPU when textureTransform is changed.
        val program = checkNotNull<Program2D>(currentProgram)
        if (program is SamplerShaderProgram) {
            // Copy the texture transformation matrix over.
            program.updateTextureMatrix(textureTransform)
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,  /*firstVertex=*/0,  /*vertexCount=*/4)
        GLUtils.checkGlErrorOrThrow("glDrawArrays")

        // Set timestamp
        EGLExt.eglPresentationTimeANDROID(eglDisplay, outputSurface!!.eglSurface, timestampNs)

        // Swap buffer
        if (!EGL14.eglSwapBuffers(eglDisplay, outputSurface.eglSurface)) {
            Log.w(
                TAG, "Failed to swap buffers with EGL error: 0x" + Integer.toHexString(
                    EGL14.eglGetError()
                )
            )
            removeOutputSurfaceInternal(surface, false)
        }
    }

    // Returns a pair of GL extension (first) and EGL extension (second) strings.
    private fun getExtensionsBeforeInitialized(
        dynamicRangeToInitialize: DynamicRange
    ): Pair<String, String> {
        GLUtils.checkInitializedOrThrow(isInitialized, false)
        try {
            createEglContext(dynamicRangeToInitialize,  /*infoBuilder=*/null)
            createTempSurface()
            makeCurrent(tempSurface)
            // eglMakeCurrent() has to be called before checking GL_EXTENSIONS.
            val glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
            val eglExtensions = EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS)
            return Pair(
                if (glExtensions != null) glExtensions else "", if (eglExtensions != null)
                    eglExtensions
                else
                    ""
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to get GL or EGL extensions: " + e.message, e)
            return Pair("", "")
        } finally {
            releaseInternal()
        }
    }

    private fun createEglContext(
        dynamicRange: DynamicRange,
        infoBuilder: GraphicDeviceInfo.Builder?
    ) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw IllegalStateException("Unable to initialize EGL14")
        }

        if (infoBuilder != null) {
            infoBuilder.setEglVersion(version[0].toString() + "." + version[1])
        }

        val rgbBits = if (dynamicRange.is10BitHdrBackport) 10 else 8
        val alphaBits = if (dynamicRange.is10BitHdrBackport) 2 else 8
        val renderType = if (dynamicRange.is10BitHdrBackport)
            EGLExt.EGL_OPENGL_ES3_BIT_KHR
        else
            EGL14.EGL_OPENGL_ES2_BIT
        // TODO(b/319277249): It will crash on older Samsung devices for HDR video 10-bit
        //  because EGLExt.EGL_RECORDABLE_ANDROID is only supported from OneUI 6.1. We need to
        //  check by GPU Driver version when new OS is release.
        val recordableAndroid =
            if (dynamicRange.is10BitHdrBackport) EGL10.EGL_DONT_CARE else EGL14.EGL_TRUE
        val attribToChooseConfig = intArrayOf(
            EGL14.EGL_RED_SIZE, rgbBits,
            EGL14.EGL_GREEN_SIZE, rgbBits,
            EGL14.EGL_BLUE_SIZE, rgbBits,
            EGL14.EGL_ALPHA_SIZE, alphaBits,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, renderType,
            EGLExt.EGL_RECORDABLE_ANDROID, recordableAndroid,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(
                eglDisplay, attribToChooseConfig, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) { "Unable to find a suitable EGLConfig" }
        val config = configs[0]
        val attribToCreateContext = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, if (dynamicRange.is10BitHdrBackport) 3 else 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT,
            attribToCreateContext, 0
        )
        GLUtils.checkEglErrorOrThrow("eglCreateContext")
        eglConfig = config
        eglContext = context

        // Confirm with query.
        val values = IntArray(1)
        EGL14.eglQueryContext(
            eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values,
            0
        )
        Log.d(TAG, "EGLContext created, client version " + values[0])
    }

    private fun createTempSurface() {
        tempSurface = GLUtils.createPBufferSurface(
            eglDisplay, requireNotNull(eglConfig),  /*width=*/1,  /*height=*/
            1
        )
    }

    protected fun makeCurrent(eglSurface: EGLSurface) {
        check(
            EGL14.eglMakeCurrent(
                eglDisplay,
                eglSurface,
                eglSurface,
                eglContext
            )
        ) { "eglMakeCurrent failed" }
    }

    protected fun useAndConfigureProgramWithTexture(textureId: Int) {
        val program = programHandles[currentInputformat]
        checkNotNull(program) { "Unable to configure program for input format: $currentInputformat" }
        if (currentProgram !== program) {
            currentProgram = program
            currentProgram!!.use()
            Log.d(
                TAG, ("Using program for input format $currentInputformat: $currentProgram")
            )
        }

        // Activate the texture
        activateExternalTexture(textureId)
    }

    private fun releaseInternal() {
        // Delete program
        for (program in programHandles.values) {
            program.delete()
        }
        programHandles = mutableMapOf<InputFormat, Program2D>()
        currentProgram = null

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )

            // Destroy EGLSurfaces
            for (outputSurface in outputSurfaceMap.values) {
                if (outputSurface.eglSurface != EGL14.EGL_NO_SURFACE) {
                    if (!EGL14.eglDestroySurface(eglDisplay, outputSurface.eglSurface)) {
                        GLUtils.checkEglErrorOrLog("eglDestroySurface")
                    }
                }
            }
            outputSurfaceMap.clear()

            // Destroy temp surface
            if (tempSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, tempSurface)
                tempSurface = EGL14.EGL_NO_SURFACE
            }

            // Destroy EGLContext and terminate display
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }

        // Reset other members
        eglConfig = null
        externalTextureId = -1
        currentInputformat = InputFormat.UNKNOWN
        currentSurface = null
        glThread = null
    }

    protected fun getOutSurfaceOrThrow(surface: Surface): OutputSurface {
        check(
            outputSurfaceMap.containsKey(surface)
        ) { "The surface is not registered." }

        return outputSurfaceMap.getValue(surface)
    }

    protected fun createOutputSurfaceInternal(surface: Surface): OutputSurface? {
        var eglSurface: EGLSurface?
        try {
            eglSurface = GLUtils.createWindowSurface(
                eglDisplay, Objects.requireNonNull<EGLConfig?>(eglConfig), surface,
                surfaceAttrib
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to create EGL surface: ${e.message}", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to create EGL surface: ${e.message}", e)
            return null
        }

        val size = GLUtils.getSurfaceSize(eglDisplay, eglSurface)
        return OutputSurface.of(eglSurface, size.width, size.height)
    }

    protected fun removeOutputSurfaceInternal(surface: Surface, unregister: Boolean) {
        // Unmake current surface.
        if (currentSurface === surface) {
            currentSurface = null
            makeCurrent(tempSurface)
        }

        // Remove cached EGL surface.
        var removedOutputSurface: OutputSurface?
        if (unregister) {
            removedOutputSurface = outputSurfaceMap.remove(surface)
        } else {
            removedOutputSurface = outputSurfaceMap.put(surface, GLUtils.NO_OUTPUT_SURFACE)
        }

        // Destroy EGL surface.
        if (removedOutputSurface != null && removedOutputSurface !== GLUtils.NO_OUTPUT_SURFACE) {
            try {
                EGL14.eglDestroySurface(eglDisplay, removedOutputSurface.eglSurface)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to destroy EGL surface: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "OpenGlRenderer"

    }
}
