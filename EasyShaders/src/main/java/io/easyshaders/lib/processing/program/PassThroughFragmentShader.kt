package io.easyshaders.lib.processing.program

import android.opengl.GLES31

class PassThroughFragmentShader(): FragmentShader() {

    override val samplerLocation: ShaderProperty<Int> by lazy {
        uniformProperty("sTexture", FragmentShaderProgramId(fragmentShaderProgramId!!.handle))
    }

    override fun source(): String = FRAGMENT_SHADER

    override fun loadLocations(fragmentShaderProgramId: FragmentShaderProgramId) {
        val samplerLoc = GLES31.glGetUniformLocation(fragmentShaderProgramId.handle, "sTexture")
    }

    override fun dispose() {
        // TODO:
    }

    override fun beforeFrameRendered() {
        // TODO:
    }
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