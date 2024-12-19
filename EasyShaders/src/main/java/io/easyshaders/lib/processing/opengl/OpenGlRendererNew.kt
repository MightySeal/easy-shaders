package io.easyshaders.lib.processing.opengl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.util.Log
import android.view.Surface
import androidx.annotation.WorkerThread
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.OpenGlRenderer
import io.easyshaders.lib.processing.program.FragmentShader
import io.easyshaders.lib.processing.program.ProgramPipeline
import io.easyshaders.lib.processing.util.GLUtils
import io.easyshaders.lib.processing.util.InputFormat
import io.easyshaders.lib.processing.util.OutputSurface
import io.easyshaders.lib.processing.utils.TAG
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean

@WorkerThread
internal class OpenGlRendererNew(
    private val openglEnvironment: OpenglEnvironment,
) {

    private val eglDisplay: EGLDisplay
        get() = openglEnvironment.eglEnv.eglDisplay
    private val eglConfig: EGLConfig
        get() = openglEnvironment.eglEnv.eglConfig!!
    private val eglContext: EGLContext
        get() = openglEnvironment.eglEnv.eglContext

    private val isInitialized: AtomicBoolean = AtomicBoolean(false)
    private val outputSurfaceMap: MutableMap<Surface, OutputSurface> = mutableMapOf()
    private var glThread: Thread? = null

    private var currentSurface: Surface? = null
    private var pipelineHandles: Map<InputFormat, ProgramPipeline> = mutableMapOf()
    private var currentProgram: ProgramPipeline? = null // TODO: use default? Make no-op implementation?
    private var currentInputformat: InputFormat = InputFormat.UNKNOWN // TODO: use unknown?

    private var externalTextureId = -1

    private lateinit var initDynamicRange: DynamicRange

    fun init(dynamicRange: DynamicRange, ) {
        try {
            this.initDynamicRange = dynamicRange
            pipelineHandles = GLUtils.createPipelines(dynamicRange)
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
    }

    fun getTextureName(): Int {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        return externalTextureId
    }

    fun setInputFormat(inputFormat: InputFormat) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        if (currentInputformat != inputFormat) {
            currentInputformat = inputFormat
            useAndConfigureProgramWithTexture(externalTextureId)
        }
    }

    fun registerOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        if (!outputSurfaceMap.containsKey(surface)) {
            outputSurfaceMap.put(surface, GLUtils.NO_OUTPUT_SURFACE)
        }
    }

    fun unregisterOutputSurface(surface: Surface) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        removeOutputSurfaceInternal(surface, true)
    }

    fun setFragmentShader(shader: FragmentShader) {
        GLUtils.checkInitializedOrThrow(isInitialized, true)
        GLUtils.checkGlThreadOrThrow(glThread)

        currentProgram?.setFragmentShader(shader)
    }

    fun release() {
        if (!isInitialized.getAndSet(false)) {
            return
        }
        GLUtils.checkGlThreadOrThrow(glThread)
        releaseInternal()
    }

    fun render(
        timestampNs: Long,
        textureTransform: FloatArray,
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
            openglEnvironment.makeCurrent(outputSurface!!.eglSurface)
            currentSurface = surface
            GLES31.glViewport(0, 0, outputSurface.width, outputSurface.height)
            GLES31.glScissor(0, 0, outputSurface.width, outputSurface.height)
        }

        // TODO(b/245855601): Upload the matrix to GPU when textureTransform is changed.
        val program = checkNotNull(currentProgram)
        program.updateTextureMatrix(textureTransform)
        program.onBeforeDraw()

        // Draw the rect.
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP,  /*firstVertex=*/0,  /*vertexCount=*/4)

        val error = GLES31.glGetError()
        if (error != GLES31.GL_NO_ERROR) {
            val log = GLES31.glGetProgramPipelineInfoLog(program.pipelineProgramId.programHandle)
            Log.e(TAG, "Pipeline error log: $log")
        }

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

    private fun removeOutputSurfaceInternal(surface: Surface, unregister: Boolean) {
        // Unmake current surface.
        if (currentSurface === surface) {
            currentSurface = null
            // openglEnvironment.makeCurrent(tempSurface)
            openglEnvironment.makeCurrentTempSurface()
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

    private fun useAndConfigureProgramWithTexture(textureId: Int) {
        val program = pipelineHandles[currentInputformat]
        checkNotNull(program) { "Unable to configure program for input format: $currentInputformat" }
        if (currentProgram !== program) {
            currentProgram = program
            currentProgram!!.use()
        }

        // Activate the texture
        activateExternalTexture(textureId)
    }

    private fun activateExternalTexture(externalTextureId: Int) {
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
        GLUtils.checkGlErrorOrThrow("glActiveTexture")

        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLUtils.checkGlErrorOrThrow("glBindTexture")
    }

    private fun getOutSurfaceOrThrow(surface: Surface): OutputSurface {
        check(
            outputSurfaceMap.containsKey(surface)
        ) { "The surface is not registered." }

        return outputSurfaceMap.getValue(surface)
    }

    private fun createOutputSurfaceInternal(surface: Surface): OutputSurface? {
        var eglSurface: EGLSurface?
        try {
            eglSurface = GLUtils.createWindowSurface(
                eglDisplay, Objects.requireNonNull<EGLConfig?>(eglConfig), surface,
                openglEnvironment.surfaceAttrib
            )
        } catch (e: IllegalStateException) {
            Log.w(OpenGlRenderer.Companion.TAG, "Failed to create EGL surface: ${e.message}", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.w(OpenGlRenderer.Companion.TAG, "Failed to create EGL surface: ${e.message}", e)
            return null
        }

        val size = GLUtils.getSurfaceSize(eglDisplay, eglSurface)
        return OutputSurface.of(eglSurface, size.width, size.height)
    }

    private fun releaseInternal() {
        // Delete program
        for (program in pipelineHandles.values) {
            program.delete()
        }
        pipelineHandles = mutableMapOf<InputFormat, ProgramPipeline>()
        currentProgram = null

        openglEnvironment.releaseInternal()
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            /*EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )*/

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
            /*if (tempSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, tempSurface)
                tempSurface = EGL14.EGL_NO_SURFACE
            }

            // Destroy EGLContext and terminate display
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }*/
            // EGL14.eglReleaseThread()
            // EGL14.eglTerminate(eglDisplay)
            // eglDisplay = EGL14.EGL_NO_DISPLAY
        }

        // Reset other members
        // eglConfig = null

        externalTextureId = -1
        currentInputformat = InputFormat.UNKNOWN
        currentSurface = null
        glThread = null
    }
}