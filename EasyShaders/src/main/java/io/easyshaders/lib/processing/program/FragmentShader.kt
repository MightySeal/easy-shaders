package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow

abstract class FragmentShader(val source: String): ShaderProgram {

    val shaderProgramId: FragmentShaderProgramId
    abstract val samplerLocation: ShaderProperty<Int>

    init {
        // TODO: Add an option to choose between eager/lazy initialization
        shaderProgramId = FragmentShaderProgramId(GLES31.glCreateShaderProgramv(GLES31.GL_FRAGMENT_SHADER, arrayOf(source)))
        checkGlErrorOrThrow("fragmentShaderProgramId $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        loadLocations(shaderProgramId)
    }

    fun loadLocations(fragmentShaderProgramId: FragmentShaderProgramId) {}
    open fun use() {}
    open fun dispose() {}
    open fun beforeFrameRendered() {}

    internal fun disposeInternal() {
        dispose()
    }
    internal fun useInternal(fragmentShaderProgramId: FragmentShaderProgramId) {
        GLES31.glProgramUniform1i(fragmentShaderProgramId.handle, samplerLocation.value, 0)
        use()
    }
}
