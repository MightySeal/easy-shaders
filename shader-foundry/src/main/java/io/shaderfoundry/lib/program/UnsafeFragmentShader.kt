package io.shaderfoundry.lib.program

import android.opengl.GLES31
import android.util.Log
import io.shaderfoundry.lib.util.TAG
import io.shaderfoundry.lib.util.GLUtils

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
        GLUtils.checkGlErrorOrThrow("glCreateShaderProgramv $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        samplerLocation = GLES31.glGetUniformLocation(shaderProgramId.handle, samplerName)
        GLUtils.checkGlErrorOrThrow("fragmentShaderProgramId $shaderProgramId $samplerName")

        props.putAll(loadProperties(shaderProgramId))
    }

    fun dispose() {
        GLES31.glDeleteShader(shaderProgramId.handle)
    }

    fun use() {
        GLES31.glProgramUniform1i(shaderProgramId.handle, samplerLocation, 0)
        GLUtils.checkGlErrorOrThrow("glProgramUniform1i")
    }

    override fun setProperty(name: String, value: Float) {
        props[name]?.let { prop ->
            when (prop.type) {
                GLES31.GL_FLOAT -> GLES31.glProgramUniform1f(shaderProgramId.handle, prop.location, value)
                else -> Log.e(TAG, "Unsupported type: ${prop.type}")
            }
        }
    }

    override fun setProperty(name: String, value: Int) {
        props[name]?.let { prop ->
            when (prop.type) {
                GLES31.GL_INT -> GLES31.glProgramUniform1i(shaderProgramId.handle, prop.location, value)
                else -> Log.e(TAG, "Unsupported type: ${prop.type}")
            }
        }
    }


    override fun setVectorProperty(name: String, x: Float, y: Float) {
        props[name]?.let { prop ->
            when (prop.type) {
                GLES31.GL_FLOAT_VEC2 -> GLES31.glProgramUniform2f(shaderProgramId.handle, prop.location, x, y)
                else -> Log.e(TAG, "Unsupported type: ${prop.type}")
            }
        }
    }

    override fun setVectorProperty(name: String, x: Int, y: Int) {
        props[name]?.let { prop ->
            when (prop.type) {
                GLES31.GL_INT_VEC2 -> GLES31.glProgramUniform2i(shaderProgramId.handle, prop.location, x, y)
                else -> Log.e(TAG, "Unsupported type: ${prop.type}")
            }
        }
    }

    // TODO: Do the same for SSBOs
    private fun loadProperties(programId: FragmentShaderProgramId): Map<String, UniformInfo> {
        val numUniforms = IntArray(1)
        GLES31.glGetProgramiv(programId.handle, GLES31.GL_ACTIVE_UNIFORMS, numUniforms, 0)
        GLUtils.checkGlErrorOrThrow("glGetProgramiv")

        val maxLength = IntArray(1)
        GLES31.glGetProgramiv(programId.handle, GLES31.GL_ACTIVE_UNIFORM_MAX_LENGTH, maxLength, 0)
        GLUtils.checkGlErrorOrThrow("glGetProgramiv")

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
            GLUtils.checkGlErrorOrThrow("glGetActiveUniform")

            val uniformName = String(nameBuffer, 0, length[0])
            val location = GLES31.glGetUniformLocation(programId.handle, uniformName)
            GLUtils.checkGlErrorOrThrow("glGetUniformLocation")

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
