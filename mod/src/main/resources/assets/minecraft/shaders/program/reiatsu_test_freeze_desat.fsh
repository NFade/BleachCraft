#version 150

// Absolute zero (VFX_STORYBOARD 1.3.3): desaturation 0..Amount plus a cool tint mix (#C8DCF0 x Amount / 2).
// The program lives in the minecraft namespace because post effect programs cannot be namespaced (1.21.1).

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform float Amount;
uniform vec3 Tint;

out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    vec3 grey = vec3(luma);
    vec3 d = mix(c, grey, clamp(Amount, 0.0, 1.0));
    // cool tint: multiply towards the tint colour, never brighter than the source
    vec3 tinted = mix(d, d * Tint * 1.15, clamp(Amount * 0.5, 0.0, 1.0));
    fragColor = vec4(tinted, 1.0);
}
