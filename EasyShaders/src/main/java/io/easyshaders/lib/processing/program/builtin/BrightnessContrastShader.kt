package io.easyshaders.lib.processing.program.builtin

import io.easyshaders.lib.processing.program.FragmentShader
import io.easyshaders.lib.processing.program.ShaderProperty
import io.easyshaders.lib.processing.program.uniformFloatProperty
import io.easyshaders.lib.processing.program.uniformIntProperty

class BrightnessContrastShader: FragmentShader(SHADER) {
    override val samplerLocation: ShaderProperty<Int> = uniformIntProperty("sTexture")

    var brightness = uniformFloatProperty("brightness")
    var contrast = uniformFloatProperty("contrast")
}


private val SHADER = """
    #version 310 es
    #extension GL_OES_EGL_image_external_essl3 : require
    
    precision mediump float;
    uniform samplerExternalOES sTexture;
    
    uniform float brightness;
    uniform float contrast;
    
    in vec2 vTextureCoord;
    
    out vec4 outColor;
    
    vec3 brightnessContrast(vec3 inColor, float brightness, float contrast) {
        return vec3((inColor.rgb - 0.5) * contrast + 0.5 + brightness);   
    }

    void main() {
        vec4 color = texture(sTexture, vTextureCoord);
        
        /*if (brightness == 0.0 && contrast == 0.0) {
            outColor = color;
        } else {
            outColor = vec4(brightnessContrast(color.rgb, brightness, contrast), 1.0);
        }*/
        
        outColor = vec4(brightnessContrast(color.rgb, brightness, contrast), 1.0);
    }
""".trimIndent().trim()
