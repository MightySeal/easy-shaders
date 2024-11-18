package io.easyshaders.data.processor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.arch.core.util.Function
import androidx.camera.core.CameraXThreads
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageProcessingUtil
import androidx.camera.core.Logger
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.ImageFormatConstants
import androidx.camera.core.impl.utils.MatrixExt
import androidx.camera.core.impl.utils.TransformUtils
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.impl.utils.futures.Futures
import androidx.camera.core.processing.OpenGlRenderer
import androidx.camera.core.processing.ShaderProvider
import androidx.camera.core.processing.SurfaceProcessorInternal
import androidx.camera.core.processing.util.GLUtils.InputFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.util.Preconditions

import com.google.auto.value.AutoValue
import com.google.common.util.concurrent.ListenableFuture
import io.easyshaders.data.processor.utils.TAG

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A default implementation of [SurfaceProcessor].
 *
 *
 *  This implementation simply copies the frame from the source to the destination with the
 * transformation defined in [SurfaceOutput.updateTransformMatrix].
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("RestrictedApi")
class DefaultSurfaceProcessor(
    dynamicRange: DynamicRange,
    shaderProviderOverrides: MutableMap<InputFormat?, ShaderProvider> = Collections.emptyMap<InputFormat?, ShaderProvider>()
) : SurfaceProcessorInternal, OnFrameAvailableListener {

    private val openGlRenderer: OpenGlRenderer

    @VisibleForTesting
    val glThread: HandlerThread = HandlerThread(CameraXThreads.TAG + "GL Thread")
    internal val glExecutor: Executor

    @VisibleForTesting
    val handler: Handler
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

    // Only access this on GL thread.
    private val pendingSnapshots: MutableList<PendingSnapshot> = ArrayList()

    /**
     * Constructs [DefaultSurfaceProcessor] with custom shaders.
     *
     * @param shaderProviderOverrides custom shader providers for OpenGL rendering, for each input
     * format.
     * @throws IllegalArgumentException if any shaderProvider override provides invalid shader.
     */
    /** Constructs [DefaultSurfaceProcessor] with default shaders.  */
    init {
        glThread.start()
        handler = Handler(glThread.looper)
        glExecutor = CameraXExecutors.newHandlerExecutor(handler)
        openGlRenderer = OpenGlRenderer()
        try {
            initGlRenderer(dynamicRange, shaderProviderOverrides)
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
            val surfaceTexture = SurfaceTexture(openGlRenderer.textureName)
            surfaceTexture.setDefaultBufferSize(
                surfaceRequest.resolution.width,
                surfaceRequest.resolution.height
            )
            val surface = Surface(surfaceTexture)
            surfaceRequest.setTransformationInfoListener(glExecutor) { transformationInfo: SurfaceRequest.TransformationInfo ->
                var inputFormat = InputFormat.DEFAULT
                if (surfaceRequest.dynamicRange.is10BitHdr
                    && transformationInfo.hasCameraTransform()
                ) {
                    inputFormat = InputFormat.YUV
                }
                openGlRenderer.setInputFormat(inputFormat)
            }
            surfaceRequest.provideSurface(surface, glExecutor) { _: SurfaceRequest.Result? ->
                surfaceRequest.clearTransformationInfoListener()
                surfaceTexture.setOnFrameAvailableListener(null)
                surfaceTexture.release()
                surface.release()
                inputSurfaceCount--
                checkReadyToRelease()
            }
            surfaceTexture.setOnFrameAvailableListener(this, handler)
        }, { surfaceRequest.willNotProvideSurface() })
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (isReleaseRequested.get()) {
            surfaceOutput.close()
            return
        }
        executeSafely({
            val surface = surfaceOutput.getSurface(glExecutor) { _: SurfaceOutput.Event? ->
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

    override fun snapshot(
        @IntRange(from = 0, to = 100) jpegQuality: Int,
        @IntRange(from = 0, to = 359) rotationDegrees: Int
    ): ListenableFuture<Void?> {
        return Futures.nonCancellationPropagating(CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<Void?> ->
            val pendingSnapshot: PendingSnapshot =
                PendingSnapshot.of(
                    jpegQuality,
                    rotationDegrees, completer
                )
            executeSafely(
                { pendingSnapshots.add(pendingSnapshot) },
                {
                    completer.setException(
                        java.lang.Exception(
                            "Failed to snapshot: OpenGLRenderer not ready."
                        )
                    )
                })
            "DefaultSurfaceProcessor#snapshot"
        })
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (isReleaseRequested.get()) {
            // Ignore frame update if released.
            return
        }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(textureMatrix)
        // Surface, size and transform matrix for JPEG Surface if exists
        var jpegOutput: Triple<Surface, Size, FloatArray>? = null

        for ((surfaceOutput, surface) in outputSurfaces) {
            surfaceOutput.updateTransformMatrix(surfaceOutputMatrix, textureMatrix)
            if (surfaceOutput.format == ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE) {
                // Render GPU output directly.
                try {
                    openGlRenderer.render(
                        surfaceTexture.timestamp, surfaceOutputMatrix,
                        surface
                    )
                } catch (e: RuntimeException) {
                    // This should not happen. However, when it happens, we catch the exception
                    // to prevent the crash.
                    Logger.e(TAG, "Failed to render with OpenGL.", e)
                }
            } else {
                Preconditions.checkState(
                    surfaceOutput.format == ImageFormat.JPEG,
                    "Unsupported format: " + surfaceOutput.format
                )
                Preconditions.checkState(jpegOutput == null, "Only one JPEG output is supported.")
                jpegOutput = Triple(
                    surface, surfaceOutput.size,
                    surfaceOutputMatrix.clone()
                )
            }
        }

        // Execute all pending snapshots.
        try {
            takeSnapshotAndDrawJpeg(jpegOutput)
        } catch (e: RuntimeException) {
            // Propagates error back to the app if failed to take snapshot.
            failAllPendingSnapshots(e)
        }
    }

    /**
     * Takes a snapshot of the current frame and draws it to given JPEG surface.
     *
     * @param jpegOutput The <Surface></Surface>, Surface size, transform matrix> tuple for drawing.
     */
    @WorkerThread
    private fun takeSnapshotAndDrawJpeg(jpegOutput: Triple<Surface, Size, FloatArray>?) {
        if (pendingSnapshots.isEmpty()) {
            // No pending snapshot requests, do nothing.
            return
        }

        // No JPEG Surface, fail all snapshot requests.
        if (jpegOutput == null) {
            failAllPendingSnapshots(Exception("Failed to snapshot: no JPEG Surface."))
            return
        }

        // Write to JPEG surface, once for each snapshot request.
        try {
            ByteArrayOutputStream().use { outputStream ->
                var jpegBytes: ByteArray? = null
                var jpegQuality = -1
                var rotationDegrees = -1
                var bitmap: Bitmap? = null
                val iterator = pendingSnapshots.iterator()
                while (iterator.hasNext()) {
                    val pendingSnapshot = iterator.next()
                    // Take a new snapshot if the rotation is different.
                    if (rotationDegrees != pendingSnapshot.rotationDegrees || bitmap == null) {
                        rotationDegrees = pendingSnapshot.rotationDegrees
                        // Recycle the previous bitmap to free up memory.
                        bitmap?.recycle()
                        bitmap = getBitmap(
                            jpegOutput.second, jpegOutput.third,
                            rotationDegrees
                        )
                        // Clear JPEG quality to force re-encoding.
                        jpegQuality = -1
                    }
                    // Re-encode the bitmap if the quality is different.
                    if (jpegQuality != pendingSnapshot.jpegQuality) {
                        outputStream.reset()
                        jpegQuality = pendingSnapshot.jpegQuality
                        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
                        jpegBytes = outputStream.toByteArray()
                    }
                    jpegBytes?.let {
                        ImageProcessingUtil.writeJpegBytesToSurface(jpegOutput.first, jpegBytes)
                    }
                    pendingSnapshot.completer?.set(null)
                    iterator.remove()
                }
            }
        } catch (e: IOException) {
            failAllPendingSnapshots(e)
        }
    }

    private fun failAllPendingSnapshots(throwable: Throwable) {
        for (pendingSnapshot in pendingSnapshots) {
            pendingSnapshot.completer?.setException(throwable)
        }
        pendingSnapshots.clear()
    }

    private fun getBitmap(
        size: Size,
        textureTransform: FloatArray,
        rotationDegrees: Int
    ): Bitmap {
        val snapshotTransform = textureTransform.clone()

        // Rotate the output if requested.
        MatrixExt.preRotate(snapshotTransform, rotationDegrees.toFloat(), 0.5f, 0.5f)

        // Flip the snapshot. This is for reverting the GL transform added in SurfaceOutputImpl.
        MatrixExt.preVerticalFlip(snapshotTransform, 0.5f)

        // Update the size based on the rotation degrees.
        val newSize = TransformUtils.rotateSize(size, rotationDegrees)

        // Take a snapshot Bitmap and compress it to JPEG.
        return openGlRenderer.snapshot(newSize, snapshotTransform)
    }

    @WorkerThread
    private fun checkReadyToRelease() {
        if (isReleased && inputSurfaceCount == 0) {
            // Once release is called, we can stop sending frame to output surfaces.
            for (surfaceOutput in outputSurfaces.keys) {
                surfaceOutput.close()
            }
            for (pendingSnapshot in pendingSnapshots) {
                pendingSnapshot.completer?.setException(
                    Exception("Failed to snapshot: DefaultSurfaceProcessor is released.")
                )
            }
            outputSurfaces.clear()
            openGlRenderer.release()
            glThread.quit()
        }
    }

    private fun initGlRenderer(
        dynamicRange: DynamicRange,
        shaderProviderOverrides: MutableMap<InputFormat?, ShaderProvider>
    ) {
        val initFuture: ListenableFuture<Void> = CallbackToFutureAdapter.getFuture(
            CallbackToFutureAdapter.Resolver<Void> { completer: CallbackToFutureAdapter.Completer<Void?> ->
                executeSafely({
                    try {
                        openGlRenderer.init(dynamicRange, shaderProviderOverrides)
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
            Logger.w(TAG, "Unable to executor runnable", e)
            onFailure.run()
        }
    }

    /**
     * A pending snapshot request to be executed on the next frame available.
     */
    @AutoValue
    internal abstract class PendingSnapshot {
        @get:IntRange(from = 0, to = 100)
        abstract val jpegQuality: Int

        @get:IntRange(from = 0, to = 359)
        abstract val rotationDegrees: Int

        abstract val completer: CallbackToFutureAdapter.Completer<Void?>?

        companion object {
            fun of(
                @IntRange(from = 0, to = 100) jpegQuality: Int,
                @IntRange(from = 0, to = 359) rotationDegrees: Int,
                completer: CallbackToFutureAdapter.Completer<Void?>
            ): AutoValueDefaultSurfaceProcessorPendingSnapshot {
                return AutoValueDefaultSurfaceProcessorPendingSnapshot(
                    jpegQuality, rotationDegrees, completer
                )
            }
        }
    }

    /**
     * Factory class that produces [DefaultSurfaceProcessor].
     *
     *
     *  This is for working around the limit that OpenGL cannot be initialized in unit tests.
     */
    object Factory {
        private var provider =
            Function<DynamicRange, SurfaceProcessorInternal> { dynamicRange: DynamicRange ->
                DefaultSurfaceProcessor(
                    dynamicRange
                )
            }

        /**
         * Creates a new [DefaultSurfaceProcessor] with no-op shader.
         */
        fun newInstance(dynamicRange: DynamicRange): SurfaceProcessorInternal {
            return provider.apply(dynamicRange)
        }

        /**
         * Overrides the [DefaultSurfaceProcessor] provider for testing.
         */
        @VisibleForTesting
        fun setProvider(
            provider: Function<DynamicRange, SurfaceProcessorInternal>
        ) {
            this.provider = provider
        }
    }
}
