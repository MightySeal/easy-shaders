package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import io.easyshaders.lib.processing.PreFrameCallback
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow

internal class UnsafeProgramPipeline: ProgramPipeline {

    private var fragmentSource: String = FRAGMENT_SHADER
    private var fragmentShader: UnsafeFragmentShader = UnsafeFragmentShader(fragmentSource)
    private var beforeRender: PreFrameCallback = object: PreFrameCallback {
        override fun onPreFrame(
            shader: FragmentShaderProgram,
            frameCount: Int,
            width: Int,
            height: Int
        ) {
            // NO-OP
        }
    }
    private var frameCount = 0

    private val vertexShader: VertexShader
    override val pipelineProgramId: ProgramId

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

    override fun updateTextureMatrix(textureTransform: FloatArray) {
        vertexShader.updateTextureMatrix(textureTransform)
    }

    override fun use() {
        GLES31.glBindProgramPipeline(pipelineProgramId.programHandle)
        checkGlErrorOrThrow("glUseProgram")

        vertexShader.use()
        fragmentShader.use()
        checkGlErrorOrThrow("glProgramUniform1i")
        // Set to default value for single camera case
    }

    override fun onBeforeDraw(width: Int, height: Int) {
        beforeRender.onPreFrame(fragmentShader, frameCount, width, height)
        frameCount++
    }

    override fun delete() {
        GLES31.glDeleteProgramPipelines(1, intArrayOf(pipelineProgramId.programHandle), 0)
    }

    override fun setFragmentShader(source: String, beforeRender: PreFrameCallback) {
        fragmentSource = source
        fragmentShader = UnsafeFragmentShader(source)
        this.beforeRender = beforeRender

        GLES31.glUseProgramStages(pipelineProgramId.programHandle, GLES31.GL_FRAGMENT_SHADER_BIT, fragmentShader.shaderProgramId.handle)
        checkGlErrorOrThrow("newShader glUseProgramStages")
        fragmentShader.use()
        checkGlErrorOrThrow("newShader useInternal")

        frameCount = 0
    }

    override fun setProperty(name: String, value: Float) {
        fragmentShader.setProperty(name, value)
    }

    companion object
}

private val FRAGMENT_SHADER = """
    #version 310 es
    #extension GL_OES_EGL_image_external_essl3 : require
    
    precision mediump float;
    uniform samplerExternalOES sTexture;
    in vec2 vTextureCoord;
    
    out vec4 outColor;

    void main() {
        outColor = texture(sTexture, vTextureCoord);
    }
""".trimIndent().trim()