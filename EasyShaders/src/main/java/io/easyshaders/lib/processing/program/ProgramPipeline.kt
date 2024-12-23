package io.easyshaders.lib.processing.program

import io.easyshaders.lib.processing.PreFrameCallback

internal interface ProgramPipeline {
    val pipelineProgramId: ProgramId

    fun updateTextureMatrix(textureTransform: FloatArray)
    fun use()
    fun onBeforeDraw(width: Int, height: Int)
    fun delete()
    fun setFragmentShader(source: String, beforeRender: PreFrameCallback)

    fun setProperty(name: String, value: Float)
    // TODO: More types

    companion object {
        operator fun invoke(): ProgramPipeline = UnsafeProgramPipeline()
    }
}

