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

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.WorkerThread
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageProcessingUtil
import androidx.core.util.Pair
import io.easyshaders.lib.processing.util.InputFormat
import io.easyshaders.lib.processing.util.GLUtils
import io.easyshaders.lib.processing.util.GLUtils.Program2D
import io.easyshaders.lib.processing.util.GLUtils.SamplerShaderProgram
import io.easyshaders.lib.processing.util.GraphicDeviceInfo
import io.easyshaders.lib.processing.util.OutputSurface
import io.easyshaders.lib.processing.util.is10BitHdrBackport
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.nio.ByteBuffer
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
    protected val mInitialized: AtomicBoolean = AtomicBoolean(false)
    protected val mOutputSurfaceMap: MutableMap<Surface?, OutputSurface> = mutableMapOf<Surface?, OutputSurface>()
    protected var mGlThread: Thread? = null
    protected var mEglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    protected var mEglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    // protected var mSurfaceAttrib: IntArray = intArrayOf(EGL14.EGL_NONE)
    protected var mSurfaceAttrib: IntArray = GLUtils.EMPTY_ATTRIBS
    protected var mEglConfig: EGLConfig? = null
    protected var mTempSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    protected var mCurrentSurface: Surface? = null
    protected var mProgramHandles: MutableMap<InputFormat?, Program2D?> = mutableMapOf<InputFormat?, Program2D?>() // TODO: clarify nulls?
    protected var mCurrentProgram: Program2D? = null
    protected var mCurrentInputformat: InputFormat = InputFormat.UNKNOWN

    private var mExternalTextureId = -1

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
        shaderOverrides: MutableMap<InputFormat?, ShaderProvider?> = mutableMapOf<InputFormat?, ShaderProvider?>()
    ): GraphicDeviceInfo {
        var dynamicRange = dynamicRange
        GLUtils.checkInitializedOrThrow(mInitialized, false)
        val infoBuilder = GraphicDeviceInfo.builder()
        try {
            if (dynamicRange.is10BitHdrBackport) {
                val extensions = getExtensionsBeforeInitialized(dynamicRange)
                val glExtensions = checkNotNull<String>(extensions.first)
                val eglExtensions = checkNotNull<String>(extensions.second)
                if (!glExtensions.contains("GL_EXT_YUV_target")) {
                    Log.w(TAG, "Device does not support GL_EXT_YUV_target. Fallback to SDR.")
                    dynamicRange = DynamicRange.SDR
                }
                mSurfaceAttrib = GLUtils.chooseSurfaceAttrib(eglExtensions, dynamicRange)
                infoBuilder.setGlExtensions(glExtensions)
                infoBuilder.setEglExtensions(eglExtensions)
            }
            createEglContext(dynamicRange, infoBuilder)
            createTempSurface()
            makeCurrent(mTempSurface)
            infoBuilder.setGlVersion(GLUtils.getGlVersionNumber())
            mProgramHandles = GLUtils.createPrograms(dynamicRange, shaderOverrides)
            mExternalTextureId = GLUtils.createTexture()
            useAndConfigureProgramWithTexture(mExternalTextureId)
        } catch (e: IllegalStateException) {
            releaseInternal()
            throw e
        } catch (e: IllegalArgumentException) {
            releaseInternal()
            throw e
        }
        mGlThread = Thread.currentThread()
        mInitialized.set(true)
        return infoBuilder.build()
    }

    /**
     * Releases the OpenGLRenderer
     *
     * @throws IllegalStateException if the caller doesn't run on the GL thread.
     */
    fun release() {
        if (!mInitialized.getAndSet(false)) {
            return
        }
        GLUtils.checkGlThreadOrThrow(mGlThread)
        releaseInternal()
    }

    /**
     * Register the output surface.
     *
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun registerOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(mInitialized, true)
        GLUtils.checkGlThreadOrThrow(mGlThread)

        if (!mOutputSurfaceMap.containsKey(surface)) {
            mOutputSurfaceMap.put(surface, GLUtils.NO_OUTPUT_SURFACE)
        }
    }

    /**
     * Unregister the output surface.
     *
     * @throws IllegalStateException if the renderer is not initialized or the caller doesn't run
     * on the GL thread.
     */
    fun unregisterOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(mInitialized, true)
        GLUtils.checkGlThreadOrThrow(mGlThread)

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
        GLUtils.checkInitializedOrThrow(mInitialized, true)
        GLUtils.checkGlThreadOrThrow(mGlThread)

        return mExternalTextureId
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
        GLUtils.checkInitializedOrThrow(mInitialized, true)
        GLUtils.checkGlThreadOrThrow(mGlThread)

        if (mCurrentInputformat != inputFormat) {
            mCurrentInputformat = inputFormat
            useAndConfigureProgramWithTexture(mExternalTextureId)
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
        GLUtils.checkInitializedOrThrow(mInitialized, true)
        GLUtils.checkGlThreadOrThrow(mGlThread)

        var outputSurface: OutputSurface? = getOutSurfaceOrThrow(surface)

        // Workaround situations that out surface is failed to create or needs to be recreated.
        if (outputSurface === GLUtils.NO_OUTPUT_SURFACE) {
            outputSurface = createOutputSurfaceInternal(surface)
            if (outputSurface == null) {
                return
            }

            mOutputSurfaceMap.put(surface, outputSurface)
        }

        // Set output surface.
        if (surface !== mCurrentSurface) {
            makeCurrent(outputSurface!!.getEglSurface())
            mCurrentSurface = surface
            GLES20.glViewport(0, 0, outputSurface.getWidth(), outputSurface.getHeight())
            GLES20.glScissor(0, 0, outputSurface.getWidth(), outputSurface.getHeight())
        }

        // TODO(b/245855601): Upload the matrix to GPU when textureTransform is changed.
        val program = checkNotNull<Program2D>(mCurrentProgram)
        if (program is SamplerShaderProgram) {
            // Copy the texture transformation matrix over.
            program.updateTextureMatrix(textureTransform)
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,  /*firstVertex=*/0,  /*vertexCount=*/4)
        GLUtils.checkGlErrorOrThrow("glDrawArrays")

        // Set timestamp
        EGLExt.eglPresentationTimeANDROID(mEglDisplay, outputSurface!!.getEglSurface(), timestampNs)

        // Swap buffer
        if (!EGL14.eglSwapBuffers(mEglDisplay, outputSurface.getEglSurface())) {
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
    ): Pair<String?, String?> {
        GLUtils.checkInitializedOrThrow(mInitialized, false)
        try {
            createEglContext(dynamicRangeToInitialize,  /*infoBuilder=*/null)
            createTempSurface()
            makeCurrent(mTempSurface)
            // eglMakeCurrent() has to be called before checking GL_EXTENSIONS.
            val glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
            val eglExtensions = EGL14.eglQueryString(mEglDisplay, EGL14.EGL_EXTENSIONS)
            return Pair<String?, String?>(
                if (glExtensions != null) glExtensions else "", if (eglExtensions != null)
                    eglExtensions
                else
                    ""
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to get GL or EGL extensions: " + e.message, e)
            return Pair<String?, String?>("", "")
        } finally {
            releaseInternal()
        }
    }

    private fun createEglContext(
        dynamicRange: DynamicRange,
        infoBuilder: GraphicDeviceInfo.Builder?
    ) {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(mEglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = EGL14.EGL_NO_DISPLAY
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
                mEglDisplay, attribToChooseConfig, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) { "Unable to find a suitable EGLConfig" }
        val config = configs[0]
        val attribToCreateContext = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, if (dynamicRange.is10BitHdrBackport) 3 else 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(
            mEglDisplay, config, EGL14.EGL_NO_CONTEXT,
            attribToCreateContext, 0
        )
        GLUtils.checkEglErrorOrThrow("eglCreateContext")
        mEglConfig = config
        mEglContext = context

        // Confirm with query.
        val values = IntArray(1)
        EGL14.eglQueryContext(
            mEglDisplay, mEglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values,
            0
        )
        Log.d(TAG, "EGLContext created, client version " + values[0])
    }

    private fun createTempSurface() {
        mTempSurface = GLUtils.createPBufferSurface(
            mEglDisplay, Objects.requireNonNull<EGLConfig?>(mEglConfig),  /*width=*/1,  /*height=*/
            1
        )
    }

    protected fun makeCurrent(eglSurface: EGLSurface) {
        check(
            EGL14.eglMakeCurrent(
                mEglDisplay,
                eglSurface,
                eglSurface,
                mEglContext
            )
        ) { "eglMakeCurrent failed" }
    }

    protected fun useAndConfigureProgramWithTexture(textureId: Int) {
        val program = mProgramHandles.get(mCurrentInputformat)
        checkNotNull(program) { "Unable to configure program for input format: " + mCurrentInputformat }
        if (mCurrentProgram !== program) {
            mCurrentProgram = program
            mCurrentProgram!!.use()
            Log.d(
                TAG, ("Using program for input format " + mCurrentInputformat + ": "
                        + mCurrentProgram)
            )
        }

        // Activate the texture
        activateExternalTexture(textureId)
    }

    private fun releaseInternal() {
        // Delete program
        for (program in mProgramHandles.values) {
            program.delete()
        }
        mProgramHandles = mutableMapOf<InputFormat?, Program2D?>()
        mCurrentProgram = null

        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )

            // Destroy EGLSurfaces
            for (outputSurface in mOutputSurfaceMap.values) {
                if (outputSurface.getEglSurface() != EGL14.EGL_NO_SURFACE) {
                    if (!EGL14.eglDestroySurface(mEglDisplay, outputSurface.getEglSurface())) {
                        GLUtils.checkEglErrorOrLog("eglDestroySurface")
                    }
                }
            }
            mOutputSurfaceMap.clear()

            // Destroy temp surface
            if (mTempSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(mEglDisplay, mTempSurface)
                mTempSurface = EGL14.EGL_NO_SURFACE
            }

            // Destroy EGLContext and terminate display
            if (mEglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(mEglDisplay, mEglContext)
                mEglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEglDisplay)
            mEglDisplay = EGL14.EGL_NO_DISPLAY
        }

        // Reset other members
        mEglConfig = null
        mExternalTextureId = -1
        mCurrentInputformat = InputFormat.UNKNOWN
        mCurrentSurface = null
        mGlThread = null
    }

    protected fun getOutSurfaceOrThrow(surface: Surface): OutputSurface {
        checkState(
            mOutputSurfaceMap.containsKey(surface),
            "The surface is not registered."
        )

        return Objects.requireNonNull<OutputSurface?>(mOutputSurfaceMap.get(surface))
    }

    protected fun createOutputSurfaceInternal(surface: Surface): OutputSurface? {
        var eglSurface: EGLSurface?
        try {
            eglSurface = GLUtils.createWindowSurface(
                mEglDisplay, Objects.requireNonNull<EGLConfig?>(mEglConfig), surface,
                mSurfaceAttrib
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to create EGL surface: " + e.message, e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to create EGL surface: " + e.message, e)
            return null
        }

        val size = GLUtils.getSurfaceSize(mEglDisplay, eglSurface)
        return OutputSurface.of(eglSurface, size.getWidth(), size.getHeight())
    }

    protected fun removeOutputSurfaceInternal(surface: Surface, unregister: Boolean) {
        // Unmake current surface.
        if (mCurrentSurface === surface) {
            mCurrentSurface = null
            makeCurrent(mTempSurface)
        }

        // Remove cached EGL surface.
        var removedOutputSurface: OutputSurface?
        if (unregister) {
            removedOutputSurface = mOutputSurfaceMap.remove(surface)
        } else {
            removedOutputSurface = mOutputSurfaceMap.put(surface, GLUtils.NO_OUTPUT_SURFACE)
        }

        // Destroy EGL surface.
        if (removedOutputSurface != null && removedOutputSurface !== GLUtils.NO_OUTPUT_SURFACE) {
            try {
                EGL14.eglDestroySurface(mEglDisplay, removedOutputSurface.getEglSurface())
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to destroy EGL surface: " + e.message, e)
            }
        }
    }

    companion object {
        private const val TAG = "OpenGlRenderer"

    }
}
