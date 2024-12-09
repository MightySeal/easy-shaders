package io.easyshaders.lib.processing.program

class PassThroughFragmentShader(): FragmentShader {

    override fun source(): String = FRAGMENT_SHADER


    override fun enable() {
        TODO("Not yet implemented")
    }

    override fun disable() {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    override fun beforeFrameRendered() {
        TODO("Not yet implemented")
    }
}

private val FRAGMENT_SHADER = """
    #version 320 es
    #extension GL_OES_EGL_image_external_essl3 : require
    
    precision mediump float;
    uniform samplerExternalOES sTexture;
    in vec2 vTextureCoord;
    
    out vec4 outColor;

    void main() {
        outColor = texture(sTexture, vTextureCoord);
    }
""".trimIndent().trim()