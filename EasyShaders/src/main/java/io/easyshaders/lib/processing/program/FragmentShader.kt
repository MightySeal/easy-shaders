package io.easyshaders.lib.processing.program

import android.opengl.EGL14
import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow

abstract class FragmentShader(val source: String): ShaderProgram {

    val shaderProgramId: FragmentShaderProgramId
    abstract val samplerLocation: ShaderProperty<Int>

    init {
        val currentContext = EGL14.eglGetCurrentContext()
        if (currentContext == EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("No EGL context is attached to the thread")
        }

        // TODO: Add an option to choose between eager/lazy initialization
        shaderProgramId = FragmentShaderProgramId(GLES31.glCreateShaderProgramv(GLES31.GL_FRAGMENT_SHADER, arrayOf(source)))
        checkGlErrorOrThrow("fragmentShaderProgramId $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        loadLocations()
    }

    fun loadLocations() {}
    open fun use() {}
    open fun dispose() {}
    open fun beforeFrameRendered() {}

    internal fun init() {

    }

    internal fun disposeInternal() {
        GLES31.glDeleteShader(shaderProgramId.handle)
        dispose()
    }
    internal fun useInternal() {
        Log.i(TAG, "========== ${this::class.simpleName}, ${hashCode()} useInternal ${shaderProgramId.handle}, ${samplerLocation.value}")
        GLES31.glProgramUniform1i(shaderProgramId.handle, samplerLocation.value, 0)
        use()
    }
}
