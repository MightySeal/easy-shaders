package io.easyshaders.data.processor

import android.content.Context
import androidx.camera.core.CameraEffect
import androidx.camera.core.DynamicRange
import androidx.camera.core.SurfaceProcessor
import androidx.core.util.Consumer
import java.util.concurrent.Executor

class DefaultCameraEffect private constructor(
    executor: Executor,
    private val surfaceProcessor: DefaultSurfaceProcessor,
    errorListener: Consumer<Throwable> = Consumer { },
): CameraEffect(
    PREVIEW or VIDEO_CAPTURE or IMAGE_CAPTURE,
    executor,
    surfaceProcessor,
    errorListener
) {

    override fun getSurfaceProcessor(): SurfaceProcessor {
        return surfaceProcessor
    }

    companion object {
        fun create(): DefaultCameraEffect {
            val processor = DefaultSurfaceProcessor.Factory.newInstance(DynamicRange.SDR)

            return DefaultCameraEffect(processor.glExecutor, processor)
        }
    }
}