#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DilationConfig {
    vec2 Direction;
    float Radius;
};

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = Direction / InSize;
    vec4 best = vec4(0.0);
    float bestDistance = 100.0;

    for (int offset = -9; offset <= 9; ++offset) {
        float distance = abs(float(offset));
        if (distance <= Radius) {
            vec4 sampleColor = texture(InSampler, texCoord + texel * float(offset));
            if (sampleColor.a > 0.02 && distance < bestDistance) {
                best = sampleColor;
                bestDistance = distance;
            }
        }
    }

    fragColor = best;
}
