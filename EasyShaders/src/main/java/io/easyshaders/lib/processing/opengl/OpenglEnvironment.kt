package io.easyshaders.lib.processing.opengl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES31
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.util.GLUtils
import io.easyshaders.lib.processing.util.is10BitHdrBackport
import io.easyshaders.lib.processing.utils.TAG
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGL10

// TODO: Private constructor
@WorkerThread
internal class OpenglEnvironment constructor() {
    private val isInitialized: AtomicBoolean = AtomicBoolean(false)
    private lateinit var initDynamicRange: DynamicRange

    private var glThread: Thread? = null
    private var tempSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    internal var surfaceAttrib: IntArray = GLUtils.EMPTY_ATTRIBS
    internal var eglEnv = EglEnv.EMPTY

    fun init(dynamicRange: DynamicRange, ) {
        this.initDynamicRange = dynamicRange
        var dynamicRange = dynamicRange
        GLUtils.checkInitializedOrThrow(isInitialized, false)

        try {
            if (dynamicRange.is10BitHdrBackport) {
                val extensions = getExtensionsBeforeInitialized(dynamicRange)
                val (glExtensions, eglExtensions) = extensions

                if (!glExtensions.contains("GL_EXT_YUV_target")) {
                    Log.w(TAG, "Device does not support GL_EXT_YUV_target. Fallback to SDR.")
                    dynamicRange = DynamicRange.SDR
                }
                surfaceAttrib = GLUtils.chooseSurfaceAttrib(eglExtensions, dynamicRange)
            }

            // TODO: Simplify with is10BitHdrBackport, bc anyway egl env is created first.
            //   Never destroyed in case it fallbacks to SDR if HDR was requested (?!)
            //   Key difference is in surface attrs
            eglEnv = createEglContext(dynamicRange)
            tempSurface = createTempSurface()
            makeCurrent(tempSurface)
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

    fun release() {
        if (!isInitialized.getAndSet(false)) {
            return
        }
        GLUtils.checkGlThreadOrThrow(glThread)
        releaseInternal()
    }


    // Returns a pair of GL extension (first) and EGL extension (second) strings.
    private fun getExtensionsBeforeInitialized(
        dynamicRangeToInitialize: DynamicRange
    ): Pair<String, String> {
        GLUtils.checkInitializedOrThrow(isInitialized, false)
        try {
            eglEnv = createEglContext(dynamicRangeToInitialize)
            tempSurface = createTempSurface()
            makeCurrent(tempSurface)
            // eglMakeCurrent() has to be called before checking GL_EXTENSIONS.
            val glExtensions = GLES31.glGetString(GLES31.GL_EXTENSIONS)
            val eglExtensions = EGL14.eglQueryString(eglEnv.eglDisplay, EGL14.EGL_EXTENSIONS)
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
    ): EglEnv {
        var eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw IllegalStateException("Unable to initialize EGL14")
        }

        val rgbBits = if (dynamicRange.is10BitHdrBackport) 10 else 8
        val alphaBits = if (dynamicRange.is10BitHdrBackport) 2 else 8
        val renderType = if (dynamicRange.is10BitHdrBackport) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
        // TODO(b/319277249): It will crash on older Samsung devices for HDR video 10-bit
        //  because EGLExt.EGL_RECORDABLE_ANDROID is only supported from OneUI 6.1. We need to
        //  check by GPU Driver version when new OS is release.
        val recordableAndroid = if (dynamicRange.is10BitHdrBackport) EGL10.EGL_DONT_CARE else EGL14.EGL_TRUE

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
        val config = configs[0] ?: throw IllegalStateException("EGLConfig was not initialized")
        val attribToCreateContext = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, if (dynamicRange.is10BitHdrBackport) 3 else 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT,
            attribToCreateContext, 0
        )

        /*val shareContext = EGL14.eglCreateContext(
            eglDisplay, config, context,
            attribToCreateContext, 0
        )*/

        GLUtils.checkEglErrorOrThrow("eglCreateContext")
        // eglShareContext = shareContext

        // Confirm with query.
        val values = IntArray(1)
        EGL14.eglQueryContext(
            eglDisplay, context, EGL14.EGL_CONTEXT_CLIENT_VERSION, values,
            0
        )
        Log.d(TAG, "EGLContext created, client version " + values[0])

        return EglEnv(eglDisplay, config, context)
    }

    private fun createTempSurface(): EGLSurface {
        return GLUtils.createPBufferSurface(
            eglEnv.eglDisplay,
            requireNotNull(eglEnv.eglConfig),
            /*width=*/ 1,
            /*height=*/ 1
        )
    }

    internal fun makeCurrentTempSurface() {
        makeCurrent(tempSurface)
    }

    internal fun makeCurrent(eglSurface: EGLSurface) {
        check(
            EGL14.eglMakeCurrent(
                eglEnv.eglDisplay,
                eglSurface,
                eglSurface,
                eglEnv.eglContext
            )
        ) { "eglMakeCurrent failed" }
    }

    internal fun releaseInternal() {
        // Delete program
        /*for (program in pipelineHandles.values) {
            program.delete()
        }
        pipelineHandles = mutableMapOf<InputFormat, ProgramPipeline>()
        currentProgram = null*/

        if (eglEnv.eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglEnv.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )

            // Destroy EGLSurfaces
            /*for (outputSurface in outputSurfaceMap.values) {
                if (outputSurface.eglSurface != EGL14.EGL_NO_SURFACE) {
                    if (!EGL14.eglDestroySurface(eglDisplay, outputSurface.eglSurface)) {
                        GLUtils.checkEglErrorOrLog("eglDestroySurface")
                    }
                }
            }
            outputSurfaceMap.clear()*/

            // Destroy temp surface
            if (tempSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglEnv.eglDisplay, tempSurface)
                tempSurface = EGL14.EGL_NO_SURFACE
            }

            // Destroy EGLContext and terminate display
            if (eglEnv.eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglEnv.eglDisplay, eglEnv.eglContext)
                // eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglEnv.eglDisplay)
            // eglDisplay = EGL14.EGL_NO_DISPLAY
        }

        // Reset other members
        // eglConfig = null
        eglEnv = EglEnv.EMPTY
        // externalTextureId = -1
        // currentInputformat = InputFormat.UNKNOWN
        // currentSurface = null
        glThread = null
    }

    companion object {
        private val instance by lazy {
            OpenglEnvironment()
        }
        fun instance() = instance
    }
}


internal data class EglEnv(
    val eglDisplay: EGLDisplay,
    val eglConfig: EGLConfig?,
    val eglContext: EGLContext,
) {
    companion object {
        val EMPTY = EglEnv(EGL14.EGL_NO_DISPLAY, null, EGL14.EGL_NO_CONTEXT)
    }
}