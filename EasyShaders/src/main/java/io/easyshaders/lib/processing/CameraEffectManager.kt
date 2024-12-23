package io.easyshaders.lib.processing

import androidx.camera.core.CameraEffect
import androidx.camera.core.DynamicRange
import androidx.camera.core.SurfaceProcessor
import androidx.core.util.Consumer
import io.easyshaders.lib.processing.concurrent.EffectHandlerExecutorService
import java.util.concurrent.Executor

class CameraEffectManager private constructor(
    executor: Executor,
    private val surfaceProcessor: DefaultSurfaceProcessor,
    errorListener: Consumer<Throwable> = Consumer { },
): CameraEffect(
    PREVIEW or VIDEO_CAPTURE or IMAGE_CAPTURE,
    executor,
    surfaceProcessor,
    errorListener
) {

    fun setEffectShaderSource(shaderSource: String, beforeRender: PreFrameCallback) {
        surfaceProcessor.setEffectShader(shaderSource, beforeRender)
    }

    fun setProperty(name: String, value: Float) {
        surfaceProcessor.setProperty(name, value)
    }

    override fun getSurfaceProcessor(): SurfaceProcessor {
        return surfaceProcessor
    }

    companion object {
        fun create(): CameraEffectManager {
            val glExecutor = EffectHandlerExecutorService.instance()
            val processor = DefaultSurfaceProcessor.Factory.newInstance(DynamicRange.SDR, glExecutor)

            return CameraEffectManager(glExecutor, processor)
        }
    }
}