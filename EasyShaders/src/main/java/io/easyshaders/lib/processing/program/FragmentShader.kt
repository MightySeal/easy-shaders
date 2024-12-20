package io.easyshaders.lib.processing.program

import android.opengl.GLES31

/**
 * Fragment shader program.
 * Implemented to have lazy initialization because it is an internal API which is guaranteed to
 * be called on a thread that has EGL context.
 */
abstract class FragmentShader(val source: String): ShaderProgram {
    internal val properties = mutableSetOf<LazyShaderProperty<out Number>>()

    internal val shaderProgramIdProp: FragmentShaderProgramIdProperty = FragmentShaderProgramIdProperty(source)
    val shaderProgramId: FragmentShaderProgramId by shaderProgramIdProp
    abstract val samplerLocation: ShaderProperty<Int>

    open fun onAttach() {}
    open fun beforeFrameRendered() {}
    open fun cleanup() {}

    fun dispose() {
        disposeInternal()
        cleanup()
    }

    internal fun disposeInternal() {
        GLES31.glDeleteShader(shaderProgramId.handle)
    }

    internal fun useInternal() {
        GLES31.glProgramUniform1i(shaderProgramId.handle, samplerLocation.value, 0)
        onAttach()
    }
}


fun FragmentShader.uniformIntProperty(
    name: String,
    default: Int? = null,
): ShaderProperty<Int> = IntLazyShaderProperty(
    name,
    this.shaderProgramIdProp,
    ShaderPropertyType.UNIFORM,
    default
).also {
    this.properties.add(it)
}

fun FragmentShader.uniformFloatProperty(
    name: String,
    default: Float? = null,
): ShaderProperty<Float> = FloatLazyShaderProperty(
    name,
    this.shaderProgramIdProp,
    ShaderPropertyType.UNIFORM,
    default
).also {
    this.properties.add(it)
}