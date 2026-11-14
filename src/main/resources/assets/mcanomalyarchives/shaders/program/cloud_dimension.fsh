#version 150
uniform sampler2D DiffuseSampler;
uniform float Entry;
uniform float Phase;
uniform float Darkness;
uniform float Distortion;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 uv = texCoord;
    // A single decaying disturbance, not a repeating flash. HUD is drawn afterwards.
    float band = step(0.7, sin(floor(uv.y * 28.0) * 2.7 + Phase * 0.18));
    uv.x += band * sin(Phase * 0.2) * 0.004 * Entry * Entry * Distortion;
    vec3 source = texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
    float luminance = dot(source, vec3(0.2126, 0.7152, 0.0722));
    // Lift unlit terrain while preserving true black sky and cloud silhouettes.
    float gray = smoothstep(0.04, 0.72, pow(luminance, 0.58));
    gray *= 1.0 - 0.85 * Entry * Entry * Darkness;
    fragColor = vec4(vec3(gray), 1.0);
}
