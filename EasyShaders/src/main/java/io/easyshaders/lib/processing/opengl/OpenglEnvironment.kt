package io.easyshaders.lib.processing.opengl

import android.opengl.EGL14
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

/*
@WorkerThread
class OpenglEnvironment {
    private val isInitialized: AtomicBoolean = AtomicBoolean(false)
    private lateinit var initDynamicRange: DynamicRange

    private var glThread: Thread? = null
    private var surfaceAttrib: IntArray = GLUtils.EMPTY_ATTRIBS

    fun init(
        dynamicRange: DynamicRange,
    ) {
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

            createEglContext(dynamicRange)
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


    // Returns a pair of GL extension (first) and EGL extension (second) strings.
    private fun getExtensionsBeforeInitialized(
        dynamicRangeToInitialize: DynamicRange
    ): Pair<String, String> {
        GLUtils.checkInitializedOrThrow(isInitialized, false)
        try {
            createEglContext(dynamicRangeToInitialize)
            tempSurface = createTempSurface()
            makeCurrent(tempSurface)
            // eglMakeCurrent() has to be called before checking GL_EXTENSIONS.
            val glExtensions = GLES31.glGetString(GLES31.GL_EXTENSIONS)
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
}*/
