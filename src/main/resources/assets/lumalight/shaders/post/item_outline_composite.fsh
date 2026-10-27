#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

uniform sampler2D OriginalSampler;
uniform sampler2D InnerSampler;
uniform sampler2D OuterSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

bool isItemMarker(float alpha) {
    return alpha > 0.08 && alpha < 0.92;
}

vec3 rarityColor(float marker) {
    if (marker < 0.30) return vec3(0.93, 0.95, 1.00); // common
    if (marker < 0.50) return vec3(1.00, 0.83, 0.20); // uncommon
    if (marker < 0.70) return vec3(0.26, 0.58, 1.00); // rare
    return vec3(0.88, 0.28, 1.00);                    // epic
}

void main() {
    vec4 original = texture(OriginalSampler, texCoord);
    vec4 inner = texture(InnerSampler, texCoord);
    vec4 outer = texture(OuterSampler, texCoord);

    // The model itself remains untouched; only pixels outside its exact silhouette are drawn.
    if (original.a > 0.02) {
        fragColor = vec4(0.0);
        return;
    }

    if (isItemMarker(inner.a)) {
        vec3 material = inner.rgb;
        float luminance = dot(material, vec3(0.2126, 0.7152, 0.0722));
        vec3 luminous = mix(material, vec3(1.0), 0.18 + max(0.0, 0.32 - luminance) * 0.45);
        fragColor = vec4(luminous, 0.90);
        return;
    }

    if (isItemMarker(outer.a)) {
        fragColor = vec4(rarityColor(outer.a), 0.98);
        return;
    }

    // Preserve vanilla/damage outlines at their normal narrow width.
    vec2 pixel = 1.0 / InSize;
    vec4 nearest = vec4(0.0);
    for (int i = 1; i <= 2; ++i) {
        float f = float(i);
        vec4 left = texture(OriginalSampler, texCoord + vec2(-pixel.x * f, 0.0));
        vec4 right = texture(OriginalSampler, texCoord + vec2(pixel.x * f, 0.0));
        vec4 up = texture(OriginalSampler, texCoord + vec2(0.0, -pixel.y * f));
        vec4 down = texture(OriginalSampler, texCoord + vec2(0.0, pixel.y * f));
        if (left.a > nearest.a) nearest = left;
        if (right.a > nearest.a) nearest = right;
        if (up.a > nearest.a) nearest = up;
        if (down.a > nearest.a) nearest = down;
    }
    fragColor = nearest.a > 0.92 ? vec4(nearest.rgb, 0.82) : vec4(0.0);
}
