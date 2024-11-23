package io.easyshaders.lib.processing.util

import android.opengl.GLES20
import androidx.camera.core.DynamicRange
import io.easyshaders.lib.processing.ShaderProvider
import io.easyshaders.lib.processing.util.GLUtils.DEFAULT_VERTEX_SHADER
import io.easyshaders.lib.processing.util.GLUtils.HDR_VERTEX_SHADER
import io.easyshaders.lib.processing.util.GLUtils.SHADER_PROVIDER_DEFAULT
import io.easyshaders.lib.processing.util.GLUtils.SHADER_PROVIDER_HDR_DEFAULT
import io.easyshaders.lib.processing.util.GLUtils.SHADER_PROVIDER_HDR_YUV
import io.easyshaders.lib.processing.util.GLUtils.TEX_BUF
import io.easyshaders.lib.processing.util.GLUtils.VAR_TEXTURE
import io.easyshaders.lib.processing.util.GLUtils.checkGlErrorOrThrow
import io.easyshaders.lib.processing.util.GLUtils.checkLocationOrThrow
import io.easyshaders.lib.processing.util.GLUtils.getFragmentShaderSource

class SamplerShaderProgram(
    dynamicRange: DynamicRange,
    shaderProvider: ShaderProvider
) : Program2D(
    if (dynamicRange.is10BitHdrBackport) HDR_VERTEX_SHADER else DEFAULT_VERTEX_SHADER,
    getFragmentShaderSource(shaderProvider)
) {
    private var samplerLoc = -1
    private var texMatrixLoc = -1
    private var texCoordLoc = -1

    constructor(
        dynamicRange: DynamicRange,
        inputFormat: InputFormat
    ) : this(dynamicRange, resolveDefaultShaderProvider(dynamicRange, inputFormat))

    init {
        loadLocations()
    }

    override fun use() {
        super.use()
        // Initialize the sampler to the correct texture unit offset
        GLES20.glUniform1i(samplerLoc, 0)

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        checkGlErrorOrThrow("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        val coordsPerTex = 2
        val texStride = 0
        GLES20.glVertexAttribPointer(
            texCoordLoc, coordsPerTex, GLES20.GL_FLOAT,  /*normalized=*/
            false, texStride, TEX_BUF
        )
        checkGlErrorOrThrow("glVertexAttribPointer")
    }

    /** Updates the texture transform matrix  */
    fun updateTextureMatrix(textureMat: FloatArray) {
        GLES20.glUniformMatrix4fv(
            texMatrixLoc,  /*count=*/1,  /*transpose=*/false,
            textureMat,  /*offset=*/0
        )
        checkGlErrorOrThrow("glUniformMatrix4fv")
    }

    override fun loadLocations() {
        super.loadLocations()
        samplerLoc = GLES20.glGetUniformLocation(programHandle, VAR_TEXTURE)
        checkLocationOrThrow(samplerLoc, VAR_TEXTURE)
        texCoordLoc = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        checkLocationOrThrow(texCoordLoc, "aTextureCoord")
        texMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        checkLocationOrThrow(texMatrixLoc, "uTexMatrix")
    }

    companion object {
        private fun resolveDefaultShaderProvider(
            dynamicRange: DynamicRange,
            inputFormat: InputFormat?
        ): ShaderProvider {
            if (dynamicRange.is10BitHdrBackport) {
                check(inputFormat != InputFormat.UNKNOWN)
                { "No default sampler shader available for $inputFormat" }

                if (inputFormat == InputFormat.YUV) {
                    return SHADER_PROVIDER_HDR_YUV
                }
                return SHADER_PROVIDER_HDR_DEFAULT
            } else {
                return SHADER_PROVIDER_DEFAULT
            }
        }
    }
}