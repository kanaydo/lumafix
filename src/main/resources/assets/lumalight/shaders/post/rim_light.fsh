#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D MaskDepthSampler;
uniform sampler2D BaseDepthSampler;
layout(std140) uniform RimData {
    vec4 OutputEffect; // width, height, thickness, strength
    vec4 DepthData; // threshold, max distance, near, far
    vec4 RimColorData; // rgb, scene influence
    vec4 Reserved; // mode, skip green foliage
    mat4 SkyInverseProjection;
    mat4 SkyInverseViewRotation;
    vec4 SkyMask; // enabled, camera Y, cloud bottom Y, zero-to-one depth flag
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float saturate(float value) { return clamp(value, 0.0, 1.0); }

// True for vanilla sky and cloud pixels (always left untouched).
// Camera-relative position of a pixel (needs the SkyMask matrices, i.e. the world pass).
vec3 relativePosition(vec2 uv, float depth) {
    float clipDepth = SkyMask.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = SkyInverseProjection * vec4(uv * 2.0 - 1.0, clipDepth, 1.0);
    view.xyz /= abs(view.w) < 1.0e-6 ? 1.0e-6 : view.w;
    return (SkyInverseViewRotation * vec4(view.xyz, 0.0)).xyz;
}

// How much the surface at this pixel faces up (|normal.y|). Each axis uses the neighbour whose
// depth is closer to the centre, so the normal is not taken across the silhouette being outlined.
float upwardFacing(vec2 uv, float depth, vec2 texel) {
    vec2 dx = vec2(texel.x, 0.0);
    vec2 dy = vec2(0.0, texel.y);
    float left = texture(DepthSampler, uv - dx).r;
    float right = texture(DepthSampler, uv + dx).r;
    float down = texture(DepthSampler, uv - dy).r;
    float up = texture(DepthSampler, uv + dy).r;
    vec3 centre = relativePosition(uv, depth);
    vec3 alongX = abs(right - depth) < abs(depth - left)
            ? relativePosition(uv + dx, right) - centre
            : centre - relativePosition(uv - dx, left);
    vec3 alongY = abs(up - depth) < abs(depth - down)
            ? relativePosition(uv + dy, up) - centre
            : centre - relativePosition(uv - dy, down);
    vec3 normal = cross(alongX, alongY);
    float normalLength = length(normal);
    return normalLength > 1.0e-8 ? abs(normal.y) / normalLength : 0.0;
}

bool isVanillaSkyOrCloud(vec2 uv, float depth) {
    if (SkyMask.x < 0.5) return false;
    if (depth <= 0.000001) return true;
    float clipDepth = SkyMask.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = SkyInverseProjection * vec4(uv * 2.0 - 1.0, clipDepth, 1.0);
    view.xyz /= abs(view.w) < 1.0e-6 ? 1.0e-6 : view.w;
    vec3 relative = (SkyInverseViewRotation * vec4(view.xyz, 0.0)).xyz;
    float worldY = relative.y + SkyMask.y;
    // Widens with distance so depth precision cannot flicker far cloud pixels in and out.
    float band = 0.15 + length(relative) * 0.004;
    return worldY >= SkyMask.z - band && worldY <= SkyMask.z + 4.0 + band;
}
float linearizeDepth(float reversedDepth) {
    float depth = 1.0 - reversedDepth;
    return (DepthData.z * DepthData.w)
        / max(DepthData.w - depth * (DepthData.w - DepthData.z), 1.0e-5);
}
float surfaceMaskAt(vec2 uv, float sceneDepth) {
    int mode = int(Reserved.x + 0.5);
    if (mode == 2) return 1.0;

    float capturedDepth = texture(MaskDepthSampler, uv).r;
    if (capturedDepth <= 0.000001) return 0.0;
    float tolerance = max(2.0e-5, fwidth(sceneDepth) * 0.5);
    float visibleMatch = 1.0 - step(tolerance, abs(sceneDepth - capturedDepth));
    if (mode == 0) return visibleMatch;

    float baseDepth = texture(BaseDepthSampler, uv).r;
    float entityReplacedTerrain = baseDepth <= 0.000001
        ? 1.0
        : step(tolerance, abs(capturedDepth - baseDepth));
    return visibleMatch * entityReplacedTerrain;
}
float getRimEdgeMask(vec2 uv, vec2 texelSize) {
    float centerDepthRaw = texture(DepthSampler, uv).r;
    if (centerDepthRaw <= 0.000001) return 0.0;
    if (isVanillaSkyOrCloud(uv, centerDepthRaw)) return 0.0;
    float surfaceMask = surfaceMaskAt(uv, centerDepthRaw);
    if (surfaceMask <= 0.0) return 0.0;
    float zCenter = linearizeDepth(centerDepthRaw);
    float distanceFade = saturate(1.0 - zCenter / max(DepthData.y, 1.0));
    if (distanceFade <= 0.0) return 0.0;
    vec2 offset = texelSize * clamp(OutputEffect.z, 1.0, 12.0);
    vec2 minUv = texelSize * 0.5;
    vec2 maxUv = vec2(1.0) - minUv;
    vec2 uvTR = clamp(uv + offset, minUv, maxUv);
    vec2 uvBL = clamp(uv - offset, minUv, maxUv);
    vec2 uvTL = clamp(uv + vec2(-offset.x, offset.y), minUv, maxUv);
    vec2 uvBR = clamp(uv + vec2(offset.x, -offset.y), minUv, maxUv);
    // Trace only a broad silhouette.  A candidate neighbour has to lie behind
    // this surface and the same direction must still be open farther away.
    // That retains a canopy outline against terrain as well as the sky, while
    // filtering the one-block depth jumps inside a leaf cluster.
    float rawTR = texture(DepthSampler, uvTR).r;
    float rawBL = texture(DepthSampler, uvBL).r;
    float rawTL = texture(DepthSampler, uvTL).r;
    float rawBR = texture(DepthSampler, uvBR).r;
    float behindTR = rawTR <= 0.000001 ? zCenter * 8.0 : linearizeDepth(rawTR) - zCenter;
    float behindBL = rawBL <= 0.000001 ? zCenter * 8.0 : linearizeDepth(rawBL) - zCenter;
    float behindTL = rawTL <= 0.000001 ? zCenter * 8.0 : linearizeDepth(rawTL) - zCenter;
    float behindBR = rawBR <= 0.000001 ? zCenter * 8.0 : linearizeDepth(rawBR) - zCenter;
    vec2 farOffset = offset * 3.0;
    float farTRRaw = texture(DepthSampler, clamp(uv + farOffset, minUv, maxUv)).r;
    float farBLRaw = texture(DepthSampler, clamp(uv - farOffset, minUv, maxUv)).r;
    float farTLRaw = texture(DepthSampler, clamp(uv + vec2(-farOffset.x, farOffset.y), minUv, maxUv)).r;
    float farBRRaw = texture(DepthSampler, clamp(uv + vec2(farOffset.x, -farOffset.y), minUv, maxUv)).r;
    float farTR = farTRRaw <= 0.000001 ? zCenter * 8.0 : linearizeDepth(farTRRaw) - zCenter;
    float farBL = farBLRaw <= 0.000001 ? zCenter * 8.0 : linearizeDepth(farBLRaw) - zCenter;
    float farTL = farTLRaw <= 0.000001 ? zCenter * 8.0 : linearizeDepth(farTLRaw) - zCenter;
    float farBR = farBRRaw <= 0.000001 ? zCenter * 8.0 : linearizeDepth(farBRRaw) - zCenter;
    float minimumGap = max(0.035 * zCenter, 0.28);
    float edgeTR = smoothstep(minimumGap, minimumGap * 2.0, behindTR) * smoothstep(minimumGap * 0.8, minimumGap * 1.6, farTR);
    float edgeBL = smoothstep(minimumGap, minimumGap * 2.0, behindBL) * smoothstep(minimumGap * 0.8, minimumGap * 1.6, farBL);
    float edgeTL = smoothstep(minimumGap, minimumGap * 2.0, behindTL) * smoothstep(minimumGap * 0.8, minimumGap * 1.6, farTL);
    float edgeBR = smoothstep(minimumGap, minimumGap * 2.0, behindBR) * smoothstep(minimumGap * 0.8, minimumGap * 1.6, farBR);
    float silhouette = max(max(edgeTR, edgeBL), max(edgeTL, edgeBR));
    // Reject flat surfaces. Raw depth is linear in screen space across any plane, so ground seen at
    // a grazing angle has a large depth gap between rows yet a near-zero second difference; without
    // this the distant ground passed the gap test and got a pale outline band. Sky samples (0) and
    // real silhouettes break the linearity and keep their outline.
    float planeError = max(abs(rawTR + rawBL - 2.0 * centerDepthRaw), abs(rawTL + rawBR - 2.0 * centerDepthRaw))
            / max(centerDepthRaw, 1.0e-6);
    silhouette *= smoothstep(0.02, 0.08, planeError);
    return silhouette * distanceFade * surfaceMask;
}
void main() {
    vec2 uv = texCoord;
    vec3 color = texture(DiffuseSampler, uv).rgb;
    float rimFactor = getRimEdgeMask(uv, 1.0 / max(OutputEffect.xy, vec2(1.0)));
    // Skip green foliage (grass, plants, leaves) unless "Outline green foliage" is on.
    // Ratios rather than absolute differences, so dark night-time foliage (dim and blue-tinted
    // by the night lightmap) still counts: green must clearly beat red, and must not be far
    // below blue (which rejects water and other genuinely blue surfaces).
    if (Reserved.y > 0.5) {
        float greenOverRed = color.g / max(color.r, 1.0e-4);
        float greenOverBlue = color.g / max(color.b, 1.0e-4);
        float foliage = smoothstep(1.08, 1.22, greenOverRed) * smoothstep(0.70, 0.88, greenOverBlue);
        // Grass block tops and other upward-facing green ground keep their outline; plants are
        // upright crossed quads and are still skipped. Only in the world pass (SkyMask set).
        if (foliage > 0.0 && SkyMask.x > 0.5) {
            float centreDepth = texture(DepthSampler, uv).r;
            foliage *= 1.0 - smoothstep(0.70, 0.90, upwardFacing(uv, centreDepth, 1.0 / max(OutputEffect.xy, vec2(1.0))));
        }
        rimFactor *= 1.0 - foliage;
    }
    if (rimFactor <= 0.0) { fragColor = vec4(max(color, vec3(0.0)), 1.0); return; }
    vec3 sceneTint = normalize(max(sqrt(max(color, vec3(0.0))), vec3(1.0e-5)));
    // The rendered material supplies the outline hue, so modded textures work without an ID table.
    vec3 tintedLight = RimColorData.rgb * mix(vec3(1.0), sceneTint, max(clamp(RimColorData.a, 0.0, 1.0), 0.68));
    vec3 glowColor = sqrt(max(color, vec3(0.04))) * tintedLight * OutputEffect.w;
    vec3 rimComposite = clamp(glowColor * rimFactor, 0.0, 1.0);
    color = 1.0 - (1.0 - color) * (1.0 - rimComposite);
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
