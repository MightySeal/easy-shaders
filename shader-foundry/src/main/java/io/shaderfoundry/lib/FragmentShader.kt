package io.shaderfoundry.lib

import io.shaderfoundry.lib.program.FragmentShaderProgram

open class FragmentShader(val source: String, val samplerName: String) {
    open fun onPreFrame(shader: FragmentShaderProgram, frameCount: Int, width: Int, height: Int) {}
    open fun onAttach(shader: FragmentShaderProgram) {}
}
