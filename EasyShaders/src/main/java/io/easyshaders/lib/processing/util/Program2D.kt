package io.easyshaders.lib.processing.util

import android.opengl.GLES20
import io.easyshaders.lib.processing.util.GLUtils.VERTEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow
import io.easyshaders.lib.processing.util.GLUtils.checkLocationOrThrow
import io.easyshaders.lib.processing.util.GLUtils.create4x4IdentityMatrix
import io.easyshaders.lib.processing.util.GLUtils.loadShader
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

abstract class Program2D protected constructor(
    vertexShaderSource: String,
    fragmentShaderSource: String
) {
    protected var programHandle: Int = 0
    protected var transMatrixLoc: Int = -1
    protected var alphaScaleLoc: Int = -1
    protected var positionLoc: Int = -1

    init {
        var vertexShader = -1
        var fragmentShader = -1
        var program = -1
        try {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
            program = GLES20.glCreateProgram()
            checkGlErrorOrThrow("glCreateProgram")
            GLES20.glAttachShader(program, vertexShader)
            checkGlErrorOrThrow("glAttachShader")
            GLES20.glAttachShader(program, fragmentShader)
            checkGlErrorOrThrow("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus,  /*offset=*/0)
            check(linkStatus[0] == GLES20.GL_TRUE) {
                "Could not link program: " + GLES20.glGetProgramInfoLog(
                    program
                )
            }
            programHandle = program
        } catch (e: IllegalStateException) {
            if (vertexShader != -1) {
                GLES20.glDeleteShader(vertexShader)
            }
            if (fragmentShader != -1) {
                GLES20.glDeleteShader(fragmentShader)
            }
            if (program != -1) {
                GLES20.glDeleteProgram(program)
            }
            throw e
        } catch (e: IllegalArgumentException) {
            if (vertexShader != -1) {
                GLES20.glDeleteShader(vertexShader)
            }
            if (fragmentShader != -1) {
                GLES20.glDeleteShader(fragmentShader)
            }
            if (program != -1) {
                GLES20.glDeleteProgram(program)
            }
            throw e
        }

        loadLocations()
    }

    /** Use this shader program  */
    open fun use() {
        // Select the program.
        GLES20.glUseProgram(programHandle)
        checkGlErrorOrThrow("glUseProgram")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(positionLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        val coordsPerVertex = 2
        val vertexStride = 0
        GLES20.glVertexAttribPointer(
            positionLoc, coordsPerVertex, GLES20.GL_FLOAT,  /*normalized=*/
            false, vertexStride, VERTEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")

        // Set to default value for single camera case
        updateTransformMatrix(create4x4IdentityMatrix())
        updateAlpha(1.0f)
    }

    /** Updates the global transform matrix  */
    fun updateTransformMatrix(transformMat: FloatArray) {
        GLES20.glUniformMatrix4fv(
            transMatrixLoc,  /*count=*/
            1,  /*transpose=*/false, transformMat,  /*offset=*/
            0
        )
        checkGlErrorOrThrow("glUniformMatrix4fv")
    }

    /** Updates the alpha of the drawn frame  */
    fun updateAlpha(alpha: Float) {
        GLES20.glUniform1f(alphaScaleLoc, alpha)
        checkGlErrorOrThrow("glUniform1f")
    }

    /**
     * Delete the shader program
     *
     *
     * Once called, this program should no longer be used.
     */
    fun delete() {
        GLES20.glDeleteProgram(programHandle)
    }

    protected open fun loadLocations() {
        positionLoc = GLES20.glGetAttribLocation(programHandle, "aPosition")
        checkLocationOrThrow(positionLoc, "aPosition")
        transMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTransMatrix")
        checkLocationOrThrow(transMatrixLoc, "uTransMatrix")
        alphaScaleLoc = GLES20.glGetUniformLocation(programHandle, "uAlphaScale")
        checkLocationOrThrow(alphaScaleLoc, "uAlphaScale")
    }
}