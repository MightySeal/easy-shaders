package io.easyshaders.lib.processing

import androidx.camera.core.SurfaceProcessor

interface ReleasableSurfaceProcessor: SurfaceProcessor {

    fun release()
}