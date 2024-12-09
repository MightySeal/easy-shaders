package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.TEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.VERTEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow
import io.easyshaders.lib.processing.util.GLUtils.create4x4IdentityMatrix

class ShaderProgram {

    // TODO: Move multiple programs here from renderer. Probably we do not need to juggle it there.
    //   Since pipelines are
    var fragmentShader: FragmentShader = PassThroughFragmentShader()
        private set(value) {
            field = value
        }
    // val pipelineId: ProgramId = createNewPipeline(fragmentShader)
    val pipelineProgramId: ProgramId
    val vertexShadeProgramId: Int
    val fragmentShaderProgramId: Int
    val texMatrixLocation: Int
    val transMatrixLoc: Int
    val positionLoc: Int
    val texCoordLoc: Int
    val samplerLoc: Int

    // Reference: https://www.khronos.org/opengl/wiki/Example/GLSL_Separate_Program_Basics
    init {
        vertexShadeProgramId = GLES31.glCreateShaderProgramv(GLES31.GL_VERTEX_SHADER, arrayOf(VERTEX_SHADER))
        checkGlErrorOrThrow("vertexShadeProgramId $vertexShadeProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(vertexShadeProgramId, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(vertexShadeProgramId))
        }

        fragmentShaderProgramId = GLES31.glCreateShaderProgramv(GLES31.GL_FRAGMENT_SHADER, arrayOf(fragmentShader.source()))
        checkGlErrorOrThrow("fragmentShaderProgramId $fragmentShaderProgramId")

        GLES31.glGetProgramiv(vertexShadeProgramId, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(fragmentShaderProgramId))
        }

        val pipelineIdHolder = IntArray(1)
        GLES31.glGenProgramPipelines(1, pipelineIdHolder, 0)
        checkGlErrorOrThrow("glGenProgramPipelines")

        GLES31.GL_INVALID_OPERATION

        val pipelineId = pipelineIdHolder[0]

        GLES31.glUseProgramStages(pipelineId, GLES31.GL_VERTEX_SHADER_BIT, vertexShadeProgramId)
        checkGlErrorOrThrow("glUseProgramStages vertex")
        GLES31.glUseProgramStages(pipelineId, GLES31.GL_FRAGMENT_SHADER_BIT, fragmentShaderProgramId)
        checkGlErrorOrThrow("glUseProgramStages fragment")

        pipelineProgramId = ProgramId(pipelineId)
        // GLint colorLoc = glGetUniformLocation(fragProg, "Color");
        // texMatrixLoc = GLES31.glGetUniformLocation(programHandle, "uTexMatrix")
        texMatrixLocation = GLES31.glGetUniformLocation(vertexShadeProgramId, "uTexMatrix")
        transMatrixLoc = GLES31.glGetUniformLocation(vertexShadeProgramId, "uTransMatrix")
        positionLoc = GLES31.glGetAttribLocation(vertexShadeProgramId, "aPosition")
        texCoordLoc = GLES31.glGetAttribLocation(vertexShadeProgramId, "aTextureCoord")

        samplerLoc = GLES31.glGetUniformLocation(fragmentShaderProgramId, "sTexture")
    }

    fun updateTextureMatrix(textureTransform: FloatArray) {

        GLES31.glProgramUniformMatrix4fv(
            vertexShadeProgramId,
            texMatrixLocation,
            /*count=*/1,
            /*transpose=*/false,
            textureTransform,
            /*offset=*/0
        )
        checkGlErrorOrThrow("glUniformMatrix4fv")
    }

    // TODO: Figure out why it is not updated now?
    fun updateTransformMatrix(transformMat: FloatArray) {
        GLES31.glProgramUniformMatrix4fv(
            vertexShadeProgramId,
            transMatrixLoc,
            /*count=*/ 1,
            /*transpose=*/ false,
            transformMat,
            /*offset=*/ 0
        )

        checkGlErrorOrThrow("glUniformMatrix4fv")
    }

    fun use() {
        GLES31.glBindProgramPipeline(pipelineProgramId.programHandle)
        checkGlErrorOrThrow("glUseProgram")

        // Enable the "aPosition" vertex attribute.
        GLES31.glEnableVertexAttribArray(positionLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        val coordsPerVertex = 2
        val vertexStride = 0
        GLES31.glVertexAttribPointer(
            positionLoc, coordsPerVertex, GLES31.GL_FLOAT,  /*normalized=*/
            false, vertexStride, VERTEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES31.glEnableVertexAttribArray(texCoordLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        val coordsPerTex = 2
        val texStride = 0
        GLES31.glVertexAttribPointer(
            texCoordLoc, coordsPerTex, GLES31.GL_FLOAT,  /*normalized=*/
            false, texStride, TEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        GLES31.glProgramUniform1i(fragmentShaderProgramId, samplerLoc, 0)
        checkGlErrorOrThrow("glProgramUniform1i")
        // Set to default value for single camera case
        updateTransformMatrix(create4x4IdentityMatrix())
        updateTextureMatrix(create4x4IdentityMatrix())

        checkGlErrorOrThrow("update values")
    }

    fun delete() {

    }

    companion object
}

fun ProgramId.delete() {
    GLES31.glDeleteProgram(programHandle)
}

private val VERTEX_SHADER = """
    #version 320 es
    
    precision mediump float;

    in vec4 aPosition;
    in vec4 aTextureCoord;
    uniform mat4 uTexMatrix;
    uniform mat4 uTransMatrix;
    
    out vec2 vTextureCoord;
    void main() {
        gl_Position = uTransMatrix * aPosition;
        vTextureCoord = (uTexMatrix * aTextureCoord).xy;   
    }
""".trimIndent().trim()