package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow

internal data class UniformInfo(
    val location: Int,
    val type: Int,
    val size: Int
)

// TODO: Try to infer the sampler name by type "samplerExternalOES"
internal class UnsafeFragmentShader(
    val source: String,
    val samplerName: String
): ShaderProgram, FragmentShaderProgram {

    private val props = mutableMapOf<String, UniformInfo>()

    val shaderProgramId: FragmentShaderProgramId
    val samplerLocation: Int

    init {
        // TODO: Add an option to choose between eager/lazy initialization
        shaderProgramId = FragmentShaderProgramId(GLES31.glCreateShaderProgramv(GLES31.GL_FRAGMENT_SHADER, arrayOf(source)))
        samplerLocation = GLES31.glGetUniformLocation(shaderProgramId.handle, samplerName)
        checkGlErrorOrThrow("fragmentShaderProgramId $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        props.putAll(loadProperties(shaderProgramId))
    }

    fun dispose() {}

    fun use() {
        GLES31.glProgramUniform1i(shaderProgramId.handle, samplerLocation, 0)
    }

    override fun setProperty(name: String, value: Float) {
        props[name]?.let { prop ->
            when (prop.type) {
                GLES31.GL_FLOAT -> GLES31.glProgramUniform1f(shaderProgramId.handle, prop.location, value)
                else -> Log.e(TAG, "Unsupported type: ${prop.type}")
            }
        }
    }

    // TODO: Do the same for SSBOs
    private fun loadProperties(programId: FragmentShaderProgramId): Map<String, UniformInfo> {
        val numUniforms = IntArray(1)
        GLES31.glGetProgramiv(programId.handle, GLES31.GL_ACTIVE_UNIFORMS, numUniforms, 0)
        checkGlErrorOrThrow("glGetProgramiv")

        val maxLength = IntArray(1)
        GLES31.glGetProgramiv(programId.handle, GLES31.GL_ACTIVE_UNIFORM_MAX_LENGTH, maxLength, 0)
        checkGlErrorOrThrow("glGetProgramiv")

        val length = IntArray(1)
        val size = IntArray(1)
        val type = IntArray(1)

        val uniformMap = mutableMapOf<String, UniformInfo>()
        for (i in 0 until numUniforms[0]) {
            val nameBuffer = ByteArray(maxLength[0])

            GLES31.glGetActiveUniform(
                programId.handle, i, maxLength[0],
                length, 0,
                size, 0,
                type, 0,
                nameBuffer, 0
            )
            checkGlErrorOrThrow("glGetActiveUniform")

            val uniformName = String(nameBuffer, 0, length[0])
            val location = GLES31.glGetUniformLocation(programId.handle, uniformName)
            checkGlErrorOrThrow("glGetUniformLocation")

            if (location != -1) {
                uniformMap[uniformName] = UniformInfo(
                    location = location,
                    type = type[0],
                    size = size[0]
                )
            }
        }

        return uniformMap
    }

}
