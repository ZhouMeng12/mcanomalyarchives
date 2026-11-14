#version 150
uniform sampler2D DiffuseSampler;
uniform float Influence;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(DiffuseSampler, texCoord);
    float gray = dot(sampleColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    // Red flowers remain vivid while most of the world loses color.
    float red = smoothstep(0.06, 0.25, sampleColor.r - max(sampleColor.g, sampleColor.b));
    vec3 color = mix(sampleColor.rgb, vec3(gray), Influence * (1.0 - red * 0.9));
    fragColor = vec4(color, sampleColor.a);
}
