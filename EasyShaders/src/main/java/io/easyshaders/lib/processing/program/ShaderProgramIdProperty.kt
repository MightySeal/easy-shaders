package io.easyshaders.lib.processing.program

import android.opengl.EGL14
import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow
import kotlin.reflect.KProperty

internal interface ShaderProgramIdProperty<T: ShaderProgramId> {
    val shaderProgramId: T
    fun isInitialized(): Boolean
    operator fun getValue(thisRef: Any, property: KProperty<*>): T
}

internal class FragmentShaderProgramIdProperty(val source: String): ShaderProgramIdProperty<FragmentShaderProgramId> {
    private var _programId: FragmentShaderProgramId? = null

    override val shaderProgramId: FragmentShaderProgramId
        get() {
            if (_programId === null) {
                val programId = createShader()

                if (programId == FragmentShaderProgramId.INVALID_SHADER) {
                    return programId
                }

                _programId = programId
                _programId!!
            }

            return _programId!!
        }

    override fun isInitialized(): Boolean = _programId != null

    override operator fun getValue(thisRef: Any, property: KProperty<*>): FragmentShaderProgramId = shaderProgramId

    private fun createShader(): FragmentShaderProgramId {
        if (EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT) {
            return FragmentShaderProgramId.INVALID_SHADER
        }

        val shaderProgramId = FragmentShaderProgramId(GLES31.glCreateShaderProgramv(GLES31.GL_FRAGMENT_SHADER, arrayOf(source)))
        checkGlErrorOrThrow("fragmentShaderProgramId $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        return shaderProgramId
    }
}