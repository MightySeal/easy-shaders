package io.easyshaders.lib.processing.program.builtin

import io.easyshaders.lib.processing.program.FragmentShader
import io.easyshaders.lib.processing.program.ShaderProperty
import io.easyshaders.lib.processing.program.uniformIntProperty

class PassThroughFragmentShader(): FragmentShader(FRAGMENT_SHADER) {
    override val samplerLocation: ShaderProperty<Int> = uniformIntProperty("sTexture")
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