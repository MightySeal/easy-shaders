package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow

class ProgramPipeline {

    private var fragmentShader: FragmentShader = PassThroughFragmentShader()
    private val vertexShader: VertexShader
    val pipelineProgramId: ProgramId

    // Reference: https://www.khronos.org/opengl/wiki/Example/GLSL_Separate_Program_Basics
    init {
        vertexShader = VertexShader()

        val pipelineIdHolder = IntArray(1)
        GLES31.glGenProgramPipelines(1, pipelineIdHolder, 0)
        checkGlErrorOrThrow("glGenProgramPipelines")

        val pipelineId = pipelineIdHolder[0]

        GLES31.glUseProgramStages(pipelineId, GLES31.GL_VERTEX_SHADER_BIT, vertexShader.shaderProgramId.handle)
        checkGlErrorOrThrow("glUseProgramStages vertex")
        GLES31.glUseProgramStages(pipelineId, GLES31.GL_FRAGMENT_SHADER_BIT, fragmentShader.shaderProgramId.handle)
        checkGlErrorOrThrow("glUseProgramStages fragment")

        pipelineProgramId = ProgramId(pipelineId)
    }

    internal fun updateTextureMatrix(textureTransform: FloatArray) {
        vertexShader.updateTextureMatrix(textureTransform)
    }

    internal fun use() {
        GLES31.glBindProgramPipeline(pipelineProgramId.programHandle)
        checkGlErrorOrThrow("glUseProgram")

        vertexShader.use()

        fragmentShader.useInternal()
        checkGlErrorOrThrow("glProgramUniform1i")
        // Set to default value for single camera case
    }

    internal fun onBeforeDraw() {
        fragmentShader.beforeFrameRendered()
    }

    internal fun delete() {
        GLES31.glDeleteProgramPipelines(1, intArrayOf(pipelineProgramId.programHandle), 0)
    }

    internal fun setFragmentShader(newShader: FragmentShader) {
        val oldShader = fragmentShader

        GLES31.glUseProgramStages(pipelineProgramId.programHandle, GLES31.GL_FRAGMENT_SHADER_BIT, newShader.shaderProgramId.handle)
        checkGlErrorOrThrow("newShader glUseProgramStages")
        newShader.useInternal()
        checkGlErrorOrThrow("newShader useInternal")

        oldShader.disposeInternal()


        // TODO: dispose previous shader
        fragmentShader = newShader
    }

    companion object
}

