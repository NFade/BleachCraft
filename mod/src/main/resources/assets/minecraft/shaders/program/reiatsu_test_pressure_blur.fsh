#version 150

// Reiatsu pressure (R11 T7): a small two-ring blur of Radius pixels (at most about 2) and a horizontal tear: a few thin bands of rows
// are shifted sideways by Tear pixels (the bands change with Phase, which the client advances twelve times a second).
// The program lives in the minecraft namespace because post effect programs cannot be namespaced (1.21.1).

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Radius;
uniform float Tear;
uniform float Phase;

in vec2 texCoord;

out vec4 fragColor;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

void main() {
    vec2 px = 1.0 / InSize;
    vec2 uv = texCoord;
    if (Tear > 0.0) {
        float band = floor(uv.y * 22.0);
        float h = hash11(band * 1.7 + Phase * 13.1);
        if (h > 0.78) {
            float dir = hash11(band + Phase * 7.3) > 0.5 ? 1.0 : -1.0;
            uv.x += dir * Tear * px.x * (0.5 + 0.5 * h);
        }
    }
    vec3 c = texture(DiffuseSampler, uv).rgb;
    if (Radius > 0.05) {
        const vec2 DIRS[8] = vec2[8](
            vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
            vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071));
        vec3 acc = c * 0.2;
        for (int i = 0; i < 8; i++) {
            acc += texture(DiffuseSampler, uv + DIRS[i] * px * Radius * 0.5).rgb * 0.05;
            acc += texture(DiffuseSampler, uv + DIRS[i] * px * Radius).rgb * 0.05;
        }
        c = acc;
    }
    fragColor = vec4(c, 1.0);
}
