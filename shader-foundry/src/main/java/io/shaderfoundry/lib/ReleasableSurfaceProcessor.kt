package io.shaderfoundry.lib

import androidx.camera.core.SurfaceProcessor

internal interface ReleasableSurfaceProcessor: SurfaceProcessor {

    fun release()
}