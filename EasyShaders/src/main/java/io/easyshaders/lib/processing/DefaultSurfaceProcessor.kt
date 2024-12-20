package io.easyshaders.lib.processing

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.util.Log
import android.view.Surface
import androidx.annotation.WorkerThread
import androidx.camera.core.DynamicRange
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceRequest
import androidx.concurrent.futures.CallbackToFutureAdapter

import com.google.common.util.concurrent.ListenableFuture
import io.easyshaders.lib.processing.concurrent.EffectHandlerExecutorService
import io.easyshaders.lib.processing.opengl.OpenGlRendererNew
import io.easyshaders.lib.processing.opengl.OpenglEnvironment
import io.easyshaders.lib.processing.program.FragmentShader
import io.easyshaders.lib.processing.util.InputFormat
import io.easyshaders.lib.processing.util.is10BitHdrBackport
import io.easyshaders.lib.processing.utils.TAG

import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A default implementation of [SurfaceProcessor].
 *
 *
 *  This implementation simply copies the frame from the source to the destination with the
 * transformation defined in [SurfaceOutput.updateTransformMatrix].
 */
class DefaultSurfaceProcessor(
    private val glExecutor: EffectHandlerExecutorService,
    dynamicRange: DynamicRange,
) : ReleasableSurfaceProcessor, OnFrameAvailableListener {

    // private val openGlRenderer: OpenGlRenderer
    private val openGlEnvironment: OpenglEnvironment
    private val openGlRenderer: OpenGlRendererNew

    private val isReleaseRequested = AtomicBoolean(false)
    private val textureMatrix = FloatArray(16)
    private val surfaceOutputMatrix = FloatArray(16)

    // Map of current set of available outputs. Only access this on GL thread.
    /* synthetic access */
    private val outputSurfaces: MutableMap<SurfaceOutput, Surface> = LinkedHashMap()

    // Only access this on GL thread.
    private var inputSurfaceCount = 0

    // Only access this on GL thread.
    private var isReleased = false

    /**
     * Constructs [DefaultSurfaceProcessor] with custom shaders.
     *
     * @throws IllegalArgumentException if any shaderProvider override provides invalid shader.
     */
    /** Constructs [DefaultSurfaceProcessor] with default shaders.  */
    init {
        // openGlRenderer = OpenGlRenderer()
        openGlEnvironment = OpenglEnvironment()
        openGlRenderer = OpenGlRendererNew(openGlEnvironment)
        // openGlRenderer = OpenGlRendererNew(OpenglEnvironment.instance())
        try {
            initGlRenderer(dynamicRange)
        } catch (e: RuntimeException) {
            release()
            throw e
        }
    }

    override fun onInputSurface(surfaceRequest: SurfaceRequest) {
        if (isReleaseRequested.get()) {
            surfaceRequest.willNotProvideSurface()
            return
        }

        executeSafely({
            inputSurfaceCount++
            val surfaceTexture = SurfaceTexture(openGlRenderer.getTextureName())
            surfaceTexture.setDefaultBufferSize(
                surfaceRequest.resolution.width,
                surfaceRequest.resolution.height
            )
            val surface = Surface(surfaceTexture)
            surfaceRequest.setTransformationInfoListener(glExecutor) { transformationInfo: SurfaceRequest.TransformationInfo ->
                var inputFormat = InputFormat.DEFAULT
                if (surfaceRequest.dynamicRange.is10BitHdrBackport && transformationInfo.hasCameraTransform()) {
                    inputFormat = InputFormat.YUV
                }
                openGlRenderer.setInputFormat(inputFormat)
            }

            surfaceRequest.provideSurface(surface, glExecutor) {
                surfaceRequest.clearTransformationInfoListener()
                surfaceTexture.setOnFrameAvailableListener(null)
                surfaceTexture.release()
                surface.release()
                inputSurfaceCount--
                checkReadyToRelease()
            }
            surfaceTexture.setOnFrameAvailableListener(this, glExecutor.handler)
        }, { surfaceRequest.willNotProvideSurface() })
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (isReleaseRequested.get()) {
            surfaceOutput.close()
            return
        }
        executeSafely({
            val surface = surfaceOutput.getSurface(glExecutor) {
                surfaceOutput.close()
                val removedSurface = outputSurfaces.remove(surfaceOutput)
                if (removedSurface != null) {
                    openGlRenderer.unregisterOutputSurface(removedSurface)
                }
            }
            openGlRenderer.registerOutputSurface(surface)
            outputSurfaces[surfaceOutput] = surface
        }, { surfaceOutput.close() })
    }

    /**
     * Release the [DefaultSurfaceProcessor].
     */
    override fun release() {
        if (isReleaseRequested.getAndSet(true)) {
            return
        }
        executeSafely({
            isReleased = true
            checkReadyToRelease()
        })
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (isReleaseRequested.get()) {
            // Ignore frame update if released.
            return
        }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(textureMatrix)

        for ((surfaceOutput, surface) in outputSurfaces) {
            surfaceOutput.updateTransformMatrix(surfaceOutputMatrix, textureMatrix)

            try {
                openGlRenderer.render(surfaceTexture.timestamp, surfaceOutputMatrix, surface)
            } catch (e: RuntimeException) {
                // This should not happen. However, when it happens, we catch the exception
                // to prevent the crash.
                Log.e(TAG, "Failed to render with OpenGL.", e)
                throw e
            }
        }
    }

    fun setEffectShader(creator: () -> FragmentShader) {
        executeSafely({
            openGlRenderer.setFragmentShader(creator())
        })
    }

    fun setEffectShader(shader: FragmentShader) {
        // openGlRenderer.setFragmentShader(shader)
        executeSafely({
            openGlRenderer.setFragmentShader(shader)
        })
    }

    @WorkerThread
    private fun checkReadyToRelease() {
        if (isReleased && inputSurfaceCount == 0) {
            // Once release is called, we can stop sending frame to output surfaces.
            for (surfaceOutput in outputSurfaces.keys) {
                surfaceOutput.close()
            }
            outputSurfaces.clear()
            openGlEnvironment.release()
            openGlRenderer.release()
            glExecutor.quitThread()
        }
    }

    private fun initGlRenderer(
        dynamicRange: DynamicRange,
    ) {
        val initFuture: ListenableFuture<Void> = CallbackToFutureAdapter.getFuture(
            CallbackToFutureAdapter.Resolver<Void> { completer: CallbackToFutureAdapter.Completer<Void?> ->
                executeSafely({
                    try {
                        openGlEnvironment.init(dynamicRange)
                        openGlRenderer.init(dynamicRange)
                        completer.set(null)
                    } catch (e: RuntimeException) {
                        completer.setException(e)
                    }
                })
                "Init GlRenderer"
            })
        try {
            initFuture.get()
        } catch (e: ExecutionException) {
            // If the cause is a runtime exception, throw it directly. Otherwise convert to runtime
            // exception and throw.
            val cause = if (e is ExecutionException) e.cause else e
            if (cause is RuntimeException) {
                throw cause
            } else {
                throw IllegalStateException("Failed to create DefaultSurfaceProcessor", cause)
            }
        } catch (e: InterruptedException) {
            val cause = if (e is ExecutionException) e.cause else e
            if (cause is RuntimeException) {
                throw cause
            } else {
                throw IllegalStateException("Failed to create DefaultSurfaceProcessor", cause)
            }
        }
    }

    private fun executeSafely(runnable: Runnable, onFailure: Runnable = Runnable {}) {
        try {
            glExecutor.execute {
                if (isReleased) {
                    onFailure.run()
                } else {
                    runnable.run()
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Unable to executor runnable", e)
            onFailure.run()
        }
    }

    /**
     * Factory class that produces [DefaultSurfaceProcessor].
     *
     *
     *  This is for working around the limit that OpenGL cannot be initialized in unit tests.
     */
    object Factory {
        /**
         * Creates a new [DefaultSurfaceProcessor] with no-op shader.
         */
        fun newInstance(dynamicRange: DynamicRange, glExecutor: EffectHandlerExecutorService): DefaultSurfaceProcessor {
            return DefaultSurfaceProcessor(glExecutor, dynamicRange)
        }
    }
}
