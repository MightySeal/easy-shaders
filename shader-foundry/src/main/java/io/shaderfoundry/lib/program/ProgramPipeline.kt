package io.shaderfoundry.lib.program

import io.shaderfoundry.lib.FragmentShader

internal interface ProgramPipeline {
    val pipelineProgramId: ProgramId

    fun updateTextureMatrix(textureTransform: FloatArray)
    fun use()
    fun onBeforeDraw(width: Int, height: Int)
    fun delete()
    fun setFragmentShader(shader: FragmentShader)
    fun getFragmentShader(): FragmentShader

    fun setProperty(name: String, value: Float)
    fun setProperty(name: String, value: Int)
    // TODO: More types

    companion object {
        operator fun invoke(): ProgramPipeline = UnsafeProgramPipeline()
    }
}

