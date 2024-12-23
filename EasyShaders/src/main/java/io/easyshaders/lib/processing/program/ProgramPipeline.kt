package io.easyshaders.lib.processing.program

import io.easyshaders.lib.processing.FragmentShader

internal interface ProgramPipeline {
    val pipelineProgramId: ProgramId

    fun updateTextureMatrix(textureTransform: FloatArray)
    fun use()
    fun onBeforeDraw(width: Int, height: Int)
    fun delete()
    fun setFragmentShader(shader: FragmentShader)

    fun setProperty(name: String, value: Float)
    // TODO: More types

    companion object {
        operator fun invoke(): ProgramPipeline = UnsafeProgramPipeline()
    }
}

