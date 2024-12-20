package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import android.util.Log
import io.easyshaders.lib.processing.util.GLUtils.TAG
import io.easyshaders.lib.processing.util.GLUtils.TEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.VERTEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow
import io.easyshaders.lib.processing.util.GLUtils.create4x4IdentityMatrix

internal class VertexShader: ShaderProgram {
    val shaderProgramId: VertexShaderProgramId
    private val texMatrixLocation: Int
    private val transMatrixLoc: Int
    private val positionLoc: Int
    private val texCoordLoc: Int

    init {
        shaderProgramId = VertexShaderProgramId(GLES31.glCreateShaderProgramv(GLES31.GL_VERTEX_SHADER, arrayOf(VERTEX_SHADER)))
        checkGlErrorOrThrow("vertexShadeProgramId $shaderProgramId")

        val linkStatus = IntArray(1)
        GLES31.glGetProgramiv(shaderProgramId.handle, GLES31.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(TAG, GLES31.glGetProgramInfoLog(shaderProgramId.handle))
        }

        texMatrixLocation = GLES31.glGetUniformLocation(shaderProgramId.handle, "uTexMatrix")
        transMatrixLoc = GLES31.glGetUniformLocation(shaderProgramId.handle, "uTransMatrix")
        positionLoc = GLES31.glGetAttribLocation(shaderProgramId.handle, "aPosition")
        texCoordLoc = GLES31.glGetAttribLocation(shaderProgramId.handle, "aTextureCoord")
    }

    internal fun use() {
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

        updateTransformMatrix(create4x4IdentityMatrix())
        updateTextureMatrix(create4x4IdentityMatrix())

        checkGlErrorOrThrow("update values")
    }

    internal fun updateTextureMatrix(textureTransform: FloatArray) {
        GLES31.glProgramUniformMatrix4fv(
            shaderProgramId.handle,
            texMatrixLocation,
            /*count=*/1,
            /*transpose=*/false,
            textureTransform,
            /*offset=*/0
        )
        checkGlErrorOrThrow("glUniformMatrix4fv")
    }

    // TODO: Figure out why it is not updated now?
    internal fun updateTransformMatrix(transformMat: FloatArray) {
        GLES31.glProgramUniformMatrix4fv(
            shaderProgramId.handle,
            transMatrixLoc,
            /*count=*/ 1,
            /*transpose=*/ false,
            transformMat,
            /*offset=*/ 0
        )

        checkGlErrorOrThrow("glUniformMatrix4fv")
    }
}

private val VERTEX_SHADER = """
    #version 310 es
    
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