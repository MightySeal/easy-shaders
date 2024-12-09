package io.easyshaders.lib.processing.program

import android.opengl.GLES32
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
        vertexShadeProgramId = GLES32.glCreateShaderProgramv(GLES32.GL_VERTEX_SHADER, arrayOf(VERTEX_SHADER))
        checkGlErrorOrThrow("vertexShadeProgramId $vertexShadeProgramId")

        val linkStatus = IntArray(1)
        GLES32.glGetProgramiv(vertexShadeProgramId, GLES32.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES32.GL_TRUE) {
            Log.e(TAG, GLES32.glGetProgramInfoLog(vertexShadeProgramId))
        }

        fragmentShaderProgramId = GLES32.glCreateShaderProgramv(GLES32.GL_FRAGMENT_SHADER, arrayOf(fragmentShader.source()))
        checkGlErrorOrThrow("fragmentShaderProgramId $fragmentShaderProgramId")

        GLES32.glGetProgramiv(vertexShadeProgramId, GLES32.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES32.GL_TRUE) {
            Log.e(TAG, GLES32.glGetProgramInfoLog(fragmentShaderProgramId))
        }

        val pipelineIdHolder = IntArray(1)
        GLES32.glGenProgramPipelines(1, pipelineIdHolder, 0)
        checkGlErrorOrThrow("glGenProgramPipelines")

        GLES32.GL_INVALID_OPERATION

        val pipelineId = pipelineIdHolder[0]

        GLES32.glUseProgramStages(pipelineId, GLES32.GL_VERTEX_SHADER_BIT, vertexShadeProgramId)
        checkGlErrorOrThrow("glUseProgramStages vertex")
        GLES32.glUseProgramStages(pipelineId, GLES32.GL_FRAGMENT_SHADER_BIT, fragmentShaderProgramId)
        checkGlErrorOrThrow("glUseProgramStages fragment")

        pipelineProgramId = ProgramId(pipelineId)
        // GLint colorLoc = glGetUniformLocation(fragProg, "Color");
        // texMatrixLoc = GLES32.glGetUniformLocation(programHandle, "uTexMatrix")
        texMatrixLocation = GLES32.glGetUniformLocation(vertexShadeProgramId, "uTexMatrix")
        transMatrixLoc = GLES32.glGetUniformLocation(vertexShadeProgramId, "uTransMatrix")
        positionLoc = GLES32.glGetAttribLocation(vertexShadeProgramId, "aPosition")
        texCoordLoc = GLES32.glGetAttribLocation(vertexShadeProgramId, "aTextureCoord")

        samplerLoc = GLES32.glGetUniformLocation(fragmentShaderProgramId, "sTexture")
    }

    fun updateTextureMatrix(textureTransform: FloatArray) {

        GLES32.glProgramUniformMatrix4fv(
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
        GLES32.glProgramUniformMatrix4fv(
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
        GLES32.glBindProgramPipeline(pipelineProgramId.programHandle)
        checkGlErrorOrThrow("glUseProgram")

        // Enable the "aPosition" vertex attribute.
        GLES32.glEnableVertexAttribArray(positionLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        val coordsPerVertex = 2
        val vertexStride = 0
        GLES32.glVertexAttribPointer(
            positionLoc, coordsPerVertex, GLES32.GL_FLOAT,  /*normalized=*/
            false, vertexStride, VERTEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES32.glEnableVertexAttribArray(texCoordLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        val coordsPerTex = 2
        val texStride = 0
        GLES32.glVertexAttribPointer(
            texCoordLoc, coordsPerTex, GLES32.GL_FLOAT,  /*normalized=*/
            false, texStride, TEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        GLES32.glProgramUniform1i(fragmentShaderProgramId, samplerLoc, 0)
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
    GLES32.glDeleteProgram(programHandle)
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