package io.shaderfoundry.lib

import androidx.camera.core.CameraEffect
import androidx.camera.core.DynamicRange
import androidx.camera.core.SurfaceProcessor
import androidx.core.util.Consumer
import io.shaderfoundry.lib.concurrent.EffectHandlerExecutorService
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

    fun setEffectShaderSource(shader: FragmentShader) {
        surfaceProcessor.setEffectShader(shader)
    }

    fun setProperty(name: String, value: Float) {
        surfaceProcessor.setProperty(name, value)
    }

    fun setProperty(name: String, value: Int) {
        surfaceProcessor.setProperty(name, value)
    }

    override fun getSurfaceProcessor(): SurfaceProcessor {
        return surfaceProcessor
    }

    companion object {
        fun create(): CameraEffectManager {
            val glExecutor = EffectHandlerExecutorService.Factory.instance()
            val processor = DefaultSurfaceProcessor.Factory.newInstance(DynamicRange.SDR, glExecutor)

            return CameraEffectManager(glExecutor, processor)
        }
    }
}