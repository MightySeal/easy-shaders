package io.easyshaders.lib.processing.program

class PassThroughFragmentShader(): FragmentShader(FRAGMENT_SHADER) {
    override val samplerLocation: ShaderProperty<Int> = uniformProperty("sTexture")
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