package io.easyshaders.lib.processing

import io.easyshaders.lib.processing.program.FragmentShaderProgram

class FragmentShader(val source: String, val samplerName: String) {
    open fun onPreFrame(shader: FragmentShaderProgram, frameCount: Int, width: Int, height: Int) {}
}
