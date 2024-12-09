package io.easyshaders.lib.processing.program

interface FragmentShader {

    fun source(): String
    fun enable()
    fun disable()
    fun dispose()
    fun beforeFrameRendered()
}
