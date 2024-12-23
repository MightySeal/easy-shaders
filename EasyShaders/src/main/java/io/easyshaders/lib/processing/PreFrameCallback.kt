package io.easyshaders.lib.processing

import io.easyshaders.lib.processing.program.FragmentShaderProgram

fun interface PreFrameCallback {
    fun onPreFrame(shader: FragmentShaderProgram, frameCount: Int, width: Int, height: Int)
}
