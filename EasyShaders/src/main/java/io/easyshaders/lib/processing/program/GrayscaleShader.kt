package io.easyshaders.lib.processing.program

class GrayscaleShader: FragmentShader(SHADER) {
    override val samplerLocation: ShaderProperty<Int> = uniformProperty("sTexture")
}


private val SHADER = """
    #version 310 es
    #extension GL_OES_EGL_image_external_essl3 : require
    
    precision mediump float;
    uniform samplerExternalOES sTexture;
    in vec2 vTextureCoord;
    
    out vec4 outColor;
    
    vec3 grayscale(vec3 color) {
        // return color * vec3(0.2126, 0.7152, 0.0722);
        // Exactly the same as:
        // return vec3(color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722); // Looks kind of better
        
        return vec3(dot(color, vec3(0.2126, 0.7152, 0.0722)));
    }

    void main() {
        vec4 color = texture(sTexture, vTextureCoord);
        
        outColor = vec4(grayscale(color.rgb), 1.0);
    }
""".trimIndent().trim()