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
    outColor = vec4(brightnessContrast(color.rgb, brightness, contrast), 1.0);
}