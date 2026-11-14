#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Age;
uniform float Strength;
uniform float CenterX;
uniform float CenterY;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    float release = 1.0 - smoothstep(51.0, 60.0, Age);
    float influence = Strength * release;
    vec2 center = vec2(CenterX, CenterY);
    vec2 offset = texCoord - center;
    vec2 aspect = vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    float radius = length(offset * aspect);
    float waveRadius = Age < 10.0 ? Age * 0.17 : max(0.0, (52.0 - Age) / 42.0) * 1.7;
    float ring = exp(-pow((radius - waveRadius) * 24.0, 2.0));
    float rewind = smoothstep(10.0, 14.0, Age) * (1.0 - smoothstep(48.0, 53.0, Age));
    vec2 bend = offset * (ring * 0.026 + rewind * 0.005 * sin(radius * 34.0 - Age * 0.6)) * influence;
    vec2 uv = clamp(texCoord + bend, vec2(0.001), vec2(0.999));
    vec3 rgb = texture(DiffuseSampler, uv).rgb;
    vec3 ghost = texture(DiffuseSampler, clamp(uv + offset * 0.014, vec2(0.001), vec2(0.999))).rgb;
    rgb = mix(rgb, ghost, rewind * influence * 0.22);
    float gray = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(rgb, vec3(gray) * vec3(0.91, 0.94, 1.03), influence * 0.78);
    float flip = (1.0 - smoothstep(6.0, 10.0, Age)) * max(ring, 0.66 * exp(-pow((Age - 2.5) / 1.2, 2.0)));
    rgb = mix(rgb, vec3(1.0) - rgb, flip * influence);
    rgb += ring * influence * vec3(0.10, 0.055, 0.16);
    float vignette = smoothstep(0.24, 0.72, length((texCoord - 0.5) * vec2(1.0, 0.85)));
    rgb *= 1.0 - vignette * influence * 0.58;
    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
