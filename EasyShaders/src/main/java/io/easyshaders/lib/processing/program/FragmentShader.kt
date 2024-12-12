package io.easyshaders.lib.processing.program

import android.opengl.GLES31

abstract class FragmentShader: ShaderProgram {

    var fragmentShaderProgramId: FragmentShaderProgramId? = null
    abstract val samplerLocation: ShaderProperty<Int>

    abstract fun source(): String
    abstract fun loadLocations(fragmentShaderProgramId: FragmentShaderProgramId)
    abstract fun dispose()
    abstract fun beforeFrameRendered()

    internal fun useInternal(fragmentShaderProgramId: FragmentShaderProgramId) {
        GLES31.glProgramUniform1i(fragmentShaderProgramId.handle, samplerLocation.value, 0)
    }
}
