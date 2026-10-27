#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform CartoonData {
    vec4 ScreenTime;  // width, height, time, daylight
    vec4 WeatherPoster; // rain, thunder, levels, strength
    vec4 Flags0; // poster, green saturation, sky outline, water rim
    vec4 Flags1; // leaf rim, chroma, night emissive, weather palette
    vec4 SunLight; // color rgb, strength
    vec4 MoonLight; // color rgb, strength
    vec4 Celestial; // enabled, smoothed sky exposure, dimension has skylight, cartoon master
    mat4 InverseProjection;
    mat4 InverseViewRotation;
    vec4 ExposureData; // adaptive exposure, strength, wetness, camera in fluid
    vec4 Effects; // god rays, water, weather, environment reaction
    vec4 SunData; // screen uv, visibility, cloud shadow strength
    vec4 WorldData; // game time, smoothed rain, smoothed thunder, after-rain amount
    vec4 SkyColor; // rgb, heat
    vec4 FogColor; // rgb, zero-to-one depth flag
    vec4 ColorGrade; // saturation, vibrance, temperature, brightness
    vec4 ToneGrade; // shadow lift, highlight compression, underwater caustics, poster dither
    vec4 VisualSky; // aurora, shooting stars, End nebula, starfield
    vec4 VisualSurface; // frost sparkles, desert glow, Nether embers, moon shadow tint
    vec4 Environment; // dimension (-1 Nether, 0 Overworld, 1 End), biome temperature, camera motion
    vec4 Comfort; // shadow chroma, white protection, black protection, low contrast
    vec4 ComfortMotion; // flash limit, reduce decorations, wet sheen, underwater depth
    vec4 CameraPosition; // absolute camera xyz; anchors procedural surface details to the world; w = vanilla cloud layer bottom Y
};
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float sat(vec3 c) { return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b); }
float hash12(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

float softNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(cell), hash12(cell + vec2(1.0, 0.0)), f.x),
               mix(hash12(cell + vec2(0.0, 1.0)), hash12(cell + vec2(1.0)), f.x), f.y);
}

vec3 reconstructCameraRelativePosition(vec2 uv, float depth) {
    float clipDepth = FogColor.a > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, clipDepth, 1.0);
    vec4 viewPosition = InverseProjection * clipPosition;
    float safeW = abs(viewPosition.w) < 1.0e-6
        ? (viewPosition.w < 0.0 ? -1.0e-6 : 1.0e-6)
        : viewPosition.w;
    viewPosition.xyz /= safeW;
    return (InverseViewRotation * vec4(viewPosition.xyz, 0.0)).xyz;
}

vec3 rainbowColor(float value) {
    return 0.54 + 0.46 * cos(6.2831853 * (value + vec3(0.00, 0.67, 0.33)));
}

void main() {
    vec2 size = max(ScreenTime.xy, vec2(1.0));
    // Use the interpolated screen-quad UV instead of raw framebuffer pixels.
    // This prevents a fixed screen-space seam when the game uses a scaled
    // render target.
    vec2 uv = texCoord;
    vec2 px = 1.0 / size;
    vec3 color = texture(SceneSampler, uv).rgb;
    float depth = texture(DepthSampler, uv).r;
    float hasSurface = step(0.000001, depth);
    vec3 worldPosition = hasSurface > 0.0
        ? reconstructCameraRelativePosition(uv, depth)
        : vec3(0.0);
    vec3 surfaceWorldPosition = worldPosition + CameraPosition.xyz;
    // Leave clear-depth sky and the vanilla cloud layer
    // (cloud bottom Y up to 4 blocks above) exactly as the game drew them.
    // The band widens with distance so depth precision cannot flicker far cloud pixels in and out.
    float cloudBand = 0.15 + length(worldPosition) * 0.004;
    bool keepVanilla = hasSurface <= 0.0
            || (surfaceWorldPosition.y >= CameraPosition.w - cloudBand && surfaceWorldPosition.y <= CameraPosition.w + 4.0 + cloudBand);
    vec3 worldNormal = vec3(0.0, 1.0, 0.0);
    if (hasSurface > 0.0) {
        vec3 positionDx = dFdx(worldPosition);
        vec3 positionDy = dFdy(worldPosition);
        vec3 geometricNormal = cross(positionDx, positionDy);
        float normalLength = length(geometricNormal);
        if (normalLength > 0.00001) {
            worldNormal = geometricNormal / normalLength;
            if (worldNormal.y < 0.0) worldNormal = -worldNormal;
        }
    }
    // Exit only after the dFdx/dFdy above so derivatives stay in uniform control
    // flow; the rest of the pass is skipped for sky and cloud pixels.
    if (keepVanilla) {
        fragColor = vec4(max(color, vec3(0.0)), 1.0);
        return;
    }
    float upwardSurface = smoothstep(0.22, 0.88, worldNormal.y);
    float decorationMotion = ComfortMotion.y > 0.5 ? 1.0 - smoothstep(0.10, 0.70, Environment.z) * 0.82 : 1.0;

    // Never warp the finished frame. Heat and fluid reactions are expressed
    // through atmosphere, surface shading and particles, keeping the camera stable.

    vec3 left = texture(SceneSampler, uv - vec2(px.x, 0.0)).rgb;
    vec3 right = texture(SceneSampler, uv + vec2(px.x, 0.0)).rgb;
    vec3 up = texture(SceneSampler, uv + vec2(0.0, px.y)).rgb;
    vec3 down = texture(SceneSampler, uv - vec2(0.0, px.y)).rgb;
    float colorEdge = clamp(length(right - left) + length(up - down), 0.0, 1.0);

    float greenMask = smoothstep(0.025, 0.24, color.g - max(color.r, color.b));
    greenMask *= smoothstep(0.05, 0.26, luma(color));
    greenMask *= 1.0 - smoothstep(0.82, 1.0, luma(color));
    if (Flags0.y > 0.001 && greenMask > 0.0) {
        float greenLuma = luma(color);
        vec3 saturatedGreen = vec3(greenLuma)
                + (color - vec3(greenLuma)) * (1.0 + Flags0.y);
        color = mix(color, max(saturatedGreen, vec3(0.0)), greenMask);
    }

    if (Flags0.z > 0.5 && depth <= 0.000001) {
        float skyEdge = smoothstep(0.08, 0.34, colorEdge);
        float bright = smoothstep(0.72, 1.0, luma(color));
        // Low-saturation bright pixels are translucent cloud layers. Applying
        // a coloured sky contour there produces a dirty grey fringe, so keep
        // the outline for the sky disc only.
        float cloudLike = (1.0 - smoothstep(0.055, 0.18, sat(color)))
                * smoothstep(0.46, 0.96, luma(color));
        skyEdge *= 1.0 - cloudLike;
        vec3 edgeTint = mix(vec3(0.20, 0.42, 0.56), vec3(1.0, 0.90, 0.52), bright);
        color = mix(color, edgeTint, skyEdge * (0.18 + bright * 0.28));
    }

    // Vanilla's sky colour can remain bright on some dimensions/resource packs
    // at night. Apply the night curve after the sky has been drawn, only to
    // clear-depth pixels, so terrain brightness is never touched.
    if (depth <= 0.000001 && Environment.x > -0.5 && Environment.x < 0.5) {
        float nightSky = smoothstep(0.46, 0.93, 1.0 - ScreenTime.w);
        float warmBiome = smoothstep(1.05, 1.55, Environment.y);
        float coldBiome = 1.0 - smoothstep(0.18, 0.48, Environment.y);
        vec3 neutralNight = vec3(0.012, 0.075, 0.155);
        vec3 warmNight = vec3(0.19, 0.045, 0.006);
        vec3 coldNight = vec3(0.004, 0.012, 0.135);
        vec3 nightTint = mix(neutralNight, warmNight, warmBiome);
        nightTint = mix(nightTint, coldNight, coldBiome);
        color = mix(color, color * 0.035 + nightTint, nightSky * 0.96);
    }

    float water = hasSurface * smoothstep(0.06, 0.34, color.b - color.r)
        * smoothstep(0.18, 0.75, color.b) * (1.0 - smoothstep(0.82, 1.0, color.r));
    if (water > 0.0 && Effects.y > 0.001) {
        // Water now owns a real animated texture. Keep its motion intact here;
        // post-processing only unifies its tint slightly and never resamples it.
        vec3 liquidTint = mix(vec3(0.055, 0.31, 0.43), mix(FogColor.rgb, SkyColor.rgb, 0.28), 0.32);
        float liquidTintAmount = clamp(water * Effects.y * 0.16, 0.0, 0.22);
        color = mix(color, liquidTint, liquidTintAmount);

        // Broken highlights are evaluated in absolute world coordinates. They
        // stay on the same blocks instead of sliding when the camera moves.
        float waveA = sin(surfaceWorldPosition.x * 0.72 + WorldData.x * 0.021
                + sin(surfaceWorldPosition.z * 0.43 - WorldData.x * 0.013) * 1.7);
        float waveB = sin(surfaceWorldPosition.z * 0.91 - WorldData.x * 0.017
                + sin(surfaceWorldPosition.x * 0.38 + WorldData.x * 0.010) * 1.4);
        float brokenWave = smoothstep(0.66, 0.96, waveA * 0.54 + waveB * 0.46);
        float viewGlance = pow(1.0 - clamp(abs(worldNormal.y), 0.0, 1.0), 2.0);
        vec3 waterReflection = mix(SkyColor.rgb, SunLight.rgb, SunData.z * 0.42);
        color += waterReflection * brokenWave * (0.025 + viewGlance * 0.08) * Effects.y;

    }

    // Water gets deeper and cooler only while the camera is actually submerged.
    // The fade is world-distance based, so it cannot create a screen-aligned band.
    if (ExposureData.w > 0.5 && hasSurface > 0.0 && ComfortMotion.w > 0.001) {
        float waterDepth = smoothstep(3.0, 28.0, length(worldPosition));
        vec3 deepWater = mix(vec3(0.015, 0.08, 0.16), FogColor.rgb * vec3(0.42, 0.66, 0.92), 0.42);
        color = mix(color, deepWater, waterDepth * ComfortMotion.w);
    }

    // Stable world-space caustics. They change the light pattern without
    // displacing the completed frame, so camera motion cannot wobble the image.
    if (ExposureData.w > 0.5 && ToneGrade.z > 0.001 && hasSurface > 0.0) {
        float causticA = sin(surfaceWorldPosition.x * 1.45 + WorldData.x * 0.075
                + sin(surfaceWorldPosition.z * 1.12 - WorldData.x * 0.052));
        float causticB = sin(surfaceWorldPosition.z * 1.73 - WorldData.x * 0.061
                + sin(surfaceWorldPosition.x * 0.91 + WorldData.x * 0.043));
        float caustic = smoothstep(0.58, 0.98, causticA * 0.5 + causticB * 0.5);
        float distanceFade = 1.0 - smoothstep(10.0, 44.0, length(worldPosition));
        color += vec3(0.20, 0.62, 0.76) * caustic * distanceFade * ToneGrade.z * 0.12;
    }

    // Rain wets every rendered surface generically. Water itself is excluded.
    float wetSurface = ExposureData.z * Effects.z * hasSurface * (1.0 - water);
    if (wetSurface > 0.001) {
        vec3 viewDirection = normalize(-worldPosition);
        float wetFresnel = pow(1.0 - abs(dot(worldNormal, viewDirection)), 3.0);
        float rainRipple = 0.78 + softNoise(surfaceWorldPosition.xz * 2.7 + WorldData.x * 0.018) * 0.22;
        float wetHighlight = (0.16 + wetFresnel * 0.84)
                * (0.30 + upwardSurface * 0.70) * rainRipple;
        // Wetness is a restrained specular lift, not a contrast filter.
        color *= 1.0 - wetSurface * 0.065;
        vec3 reflectedSky = mix(FogColor.rgb, SkyColor.rgb, 0.72);
        color += reflectedSky * wetHighlight * wetSurface * ComfortMotion.z * 0.62;
    }

    float green = smoothstep(0.035, 0.25, color.g - max(color.r, color.b));
    if (Flags1.x > 0.5 && green > 0.0 && depth > 0.000001) {
        // Second difference of depth, relative to the pixel's depth: reversed-Z depth is linear
        // in screen space across any flat surface, so this stays ~0 on ground seen at a grazing
        // angle and only fires at real silhouettes (a first difference lit up distant grass).
        float depthEdge = abs(texture(DepthSampler, uv + px).r + texture(DepthSampler, uv - px).r - 2.0 * depth)
                / max(depth, 1.0e-6);
        float rim = smoothstep(0.02, 0.15, depthEdge) * green * ScreenTime.w;
        color = 1.0 - (1.0 - color) * (1.0 - vec3(0.38, 0.78, 0.12) * rim * 0.34);
    }

    if (Flags1.y > 0.5 && sat(color) > 0.22 && colorEdge > 0.10) {
        float cyanGreen = smoothstep(0.0, 0.22, color.g + color.b - color.r * 1.7);
        vec3 split = vec3(texture(SceneSampler, uv + vec2(px.x * 1.5, 0.0)).r,
                          color.g,
                          texture(SceneSampler, uv - vec2(px.x * 1.5, 0.0)).b);
        color = mix(color, split, cyanGreen * colorEdge * 0.18);
    }

    // Lightweight material finish pack.  These gates only adjust matching
    // pixels and do not allocate buffers or scan the world.
    if (hasSurface > 0.0) {
        float ml = luma(color), ms = sat(color);
        float daylight = smoothstep(0.44, 0.78, ScreenTime.w);
        float cold = 1.0 - smoothstep(0.18, 0.48, Environment.y);
        float ice = smoothstep(0.45, 0.80, color.b) * (1.0 - smoothstep(0.13, 0.32, ms)) * (1.0 - water);
        float sand = smoothstep(0.45, 0.90, color.r) * smoothstep(0.30, 0.78, color.g) * (1.0 - smoothstep(0.38, 0.68, color.b));
        float lava = smoothstep(0.62, 0.90, color.r) * smoothstep(0.05, 0.48, color.g) * (1.0 - smoothstep(0.24, 0.50, color.b));
        float amethyst = smoothstep(0.14, 0.52, color.b - color.g) * smoothstep(0.06, 0.42, color.r - color.g);
        float copper = smoothstep(0.035, 0.28, color.g - color.r * 0.72) * (1.0 - smoothstep(0.34, 0.64, color.b));
        float moss = smoothstep(0.08, 0.29, color.g - max(color.r, color.b)) * smoothstep(0.08, 0.58, ml);
        float snow = smoothstep(0.68, 0.92, ml) * (1.0 - smoothstep(0.08, 0.24, ms));
        float leaf = moss * smoothstep(0.16, 0.50, ms);
        float stone = (1.0 - smoothstep(0.11, 0.29, ms)) * smoothstep(0.11, 0.55, ml);
        float terracotta = smoothstep(0.35, 0.74, color.r) * smoothstep(0.04, 0.25, color.r - color.g) * (1.0 - smoothstep(0.42, 0.68, color.b));
        float metallic = (1.0 - smoothstep(0.055, 0.19, ms))
                * smoothstep(0.24, 0.72, ml) * (1.0 - smoothstep(0.82, 0.97, ml));
        vec3 viewDirection = normalize(-worldPosition);
        vec3 sunDirection = normalize(vec3(-0.42, 0.78, 0.46));
        vec3 halfDirection = normalize(viewDirection + sunDirection);
        float materialGlint = pow(max(dot(worldNormal, halfDirection), 0.0), 22.0) * SunData.z;
        color += vec3(0.010, 0.030, 0.050) * ice * (0.35 + cold * 0.35);
        color += mix(vec3(0.50, 0.72, 0.96), SunLight.rgb, 0.34)
                * materialGlint * ice * 0.16;
        color = mix(color, color * vec3(1.018, 1.006, 0.972), sand * daylight * 0.36);
        color += vec3(0.070, 0.016, 0.003) * lava;
        color += vec3(0.022, 0.008, 0.044) * amethyst;
        color = mix(color, color * vec3(0.98, 1.016, 1.006), copper * 0.24);
        color += vec3(0.005, 0.020, 0.006) * moss + vec3(0.010, 0.030, 0.006) * leaf * daylight;
        color = mix(color, color * vec3(0.988, 1.0, 1.018), snow * cold * 0.28);
        color += vec3(0.006) * stone * colorEdge;
        color += mix(vec3(0.72, 0.78, 0.86), SunLight.rgb, 0.28)
                * materialGlint * metallic * 0.12;
        color = mix(color, color * vec3(1.020, 0.992, 0.970), terracotta * daylight * 0.24);
    }


    if (Flags1.z > 0.5 && ScreenTime.w < 0.48) {
        vec2 haloPx = px * 3.0;
        vec3 h0 = texture(SceneSampler, uv + vec2(haloPx.x, 0.0)).rgb;
        vec3 h1 = texture(SceneSampler, uv - vec2(haloPx.x, 0.0)).rgb;
        vec3 h2 = texture(SceneSampler, uv + vec2(0.0, haloPx.y)).rgb;
        vec3 h3 = texture(SceneSampler, uv - vec2(0.0, haloPx.y)).rgb;
        vec3 source = max(max(h0, h1), max(h2, h3));
        float sourceGate = smoothstep(0.72, 0.98, max(max(source.r, source.g), source.b)) * sat(source);
        float night = 1.0 - smoothstep(0.12, 0.55, ScreenTime.w);
        color = 1.0 - (1.0 - color) * (1.0 - source * sourceGate * night * 0.18);
    }

    if (Flags1.w > 0.5) {
        float rain = WeatherPoster.x, thunder = WeatherPoster.y;
        vec3 rainTint = vec3(0.72, 0.86, 1.0);
        vec3 stormTint = vec3(0.58, 0.48, 0.82);
        color = mix(color, color * rainTint, rain * 0.18);
        color = mix(color, color * stormTint, thunder * 0.24);
    }

    // A real lightning entity briefly lights the complete atmosphere. The
    // signal is smoothed on the CPU, avoiding a single-frame white flash.
    if (SkyColor.w > 0.001) {
        float skyBoost = depth <= 0.000001 ? 1.0 : 0.46 + colorEdge * 0.22;
        color = 1.0 - (1.0 - color) * (1.0 - vec3(0.62, 0.72, 1.0) * SkyColor.w * skyBoost * 0.38 * ComfortMotion.x);
    }

    // Low-cost depth-masked sunlight shafts with a stable screen-space origin.
    if (Effects.x > 0.001 && SunData.z > 0.001) {
        vec2 rayVector = (SunData.xy - uv) / 7.0;
        vec2 rayUv = uv;
        float illumination = 1.0;
        float shaft = 0.0;
        for (int sampleIndex = 0; sampleIndex < 7; sampleIndex++) {
            rayUv = clamp(rayUv + rayVector, px * 0.5, vec2(1.0) - px * 0.5);
            float sampleDepth = texture(DepthSampler, rayUv).r;
            shaft += (1.0 - step(0.000001, sampleDepth)) * illumination;
            illumination *= 0.86;
        }
        float radial = 1.0 - smoothstep(0.10, 1.05, distance(uv, SunData.xy));
        color += vec3(1.0, 0.78, 0.48) * shaft * (1.0 / 7.0)
                * radial * Effects.x * SunData.z * 0.34;
    }

    // A rainbow lingers while wet surfaces dry after rain.
    if (WorldData.w > 0.001 && depth <= 0.000001 && SunData.z > 0.05) {
        vec2 aspectPoint = vec2((uv.x - 0.72) * size.x / size.y, uv.y + 0.18);
        float radius = length(aspectPoint);
        float bandCoordinate = (radius - 0.69) / 0.075;
        float arc = smoothstep(0.0, 0.18, bandCoordinate)
                * (1.0 - smoothstep(0.82, 1.0, bandCoordinate))
                * smoothstep(-0.05, 0.30, uv.y);
        color += rainbowColor(clamp(bandCoordinate, 0.0, 1.0))
                * arc * WorldData.w * Effects.z * SunData.z * 0.30;
    }

    // Decorative atmosphere stays in world/screen space and only affects the sky.
    // It is deliberately dim, and fades while the player turns the camera.
    if (depth <= 0.000001 && decorationMotion > 0.001) {
        float night = smoothstep(0.52, 0.92, 1.0 - ScreenTime.w);
        float clearSky = 1.0 - WeatherPoster.x * 0.86 - WeatherPoster.y * 0.45;
        if (Environment.x > -0.5 && Environment.x < 0.5 && night > 0.001 && VisualSky.w > 0.001) {
            vec2 starCell = floor(uv * vec2(420.0, 235.0));
            float starSeed = hash12(starCell);
            vec2 starLocal = fract(uv * vec2(420.0, 235.0)) - 0.5;
            float starShape = 1.0 - smoothstep(0.045, 0.19, length(starLocal));
            float starGate = smoothstep(0.986, 0.9995, starSeed);
            float hueSeed = hash12(starCell + 19.37);
            vec3 starTint = mix(vec3(0.58, 0.72, 1.0), vec3(1.0, 0.76, 0.48), hueSeed);
            float galaxyAxis = abs((uv.y - 0.57) + (uv.x - 0.5) * 0.24);
            float galaxy = (1.0 - smoothstep(0.055, 0.21, galaxyAxis))
                    * smoothstep(0.42, 0.80, softNoise(uv * vec2(11.0, 6.0) + 4.7));
            color += starTint * starShape * starGate * night * clearSky * VisualSky.w * 0.72;
            color += vec3(0.16, 0.20, 0.38) * galaxy * night * clearSky * VisualSky.w * 0.10;
        }
        if (Environment.x > -0.5 && Environment.x < 0.5 && Environment.y < 0.32 && night > 0.001) {
            float curtain = sin(uv.x * 18.0 + WorldData.x * 0.010 + sin(uv.x * 4.0 - WorldData.x * 0.004) * 2.4);
            float auroraBand = smoothstep(0.48, 0.90, curtain * 0.5 + 0.5)
                    * smoothstep(0.16, 0.72, uv.y) * (1.0 - smoothstep(0.70, 0.98, uv.y));
            float auroraNoise = softNoise(vec2(uv.x * 14.0 + WorldData.x * 0.003, uv.y * 7.0));
            vec3 aurora = mix(vec3(0.12, 0.64, 0.46), vec3(0.30, 0.34, 0.78), auroraNoise);
            color += aurora * auroraBand * VisualSky.x * night * clearSky * decorationMotion * 0.18;

            float cycle = fract(WorldData.x / 480.0);
            float starActive = smoothstep(0.06, 0.10, cycle) * (1.0 - smoothstep(0.10, 0.17, cycle));
            vec2 starOrigin = vec2(fract(floor(WorldData.x / 480.0) * 0.618), 0.70);
            vec2 starDelta = uv - starOrigin;
            float starLine = 1.0 - smoothstep(0.0015, 0.005, abs(starDelta.y - starDelta.x * 0.28));
            float starRange = smoothstep(-0.02, 0.0, starDelta.x) * (1.0 - smoothstep(0.10, 0.16, starDelta.x));
            color += vec3(0.72, 0.86, 1.0) * starLine * starRange * starActive * VisualSky.y * night * clearSky * decorationMotion * 0.30;
        }
        if (Environment.x > 0.5) {
            float nebula = softNoise(uv * vec2(5.0, 3.0) + vec2(WorldData.x * 0.001, -WorldData.x * 0.0007));
            nebula = smoothstep(0.58, 0.86, nebula) * smoothstep(0.12, 0.88, uv.y);
            color += mix(vec3(0.18, 0.10, 0.36), vec3(0.42, 0.17, 0.52), uv.x) * nebula * VisualSky.z * decorationMotion * 0.16;
        }
    }

    // Biome accents are subtle surface glints, never a screen-space distortion.
    if (hasSurface > 0.0 && decorationMotion > 0.001) {
        float distanceFade = smoothstep(7.0, 34.0, length(worldPosition));
        float grain = hash12(floor(surfaceWorldPosition.xz * 2.6));
        if (Environment.x > -0.5 && Environment.x < 0.5 && Environment.y < 0.25) {
            float frost = smoothstep(0.985, 1.0, grain) * smoothstep(0.18, 0.70, 1.0 - ScreenTime.w);
            color += vec3(0.48, 0.72, 0.96) * frost * VisualSurface.x * decorationMotion * 0.18;
        }
        if (Environment.x > -0.5 && Environment.x < 0.5 && Environment.y > 1.35) {
            float warmFacing = smoothstep(0.30, 0.86, ScreenTime.w) * distanceFade;
            color += vec3(0.34, 0.17, 0.045) * warmFacing * VisualSurface.y * decorationMotion * 0.16;
        }
        if (Environment.x < -0.5) {
            float ember = smoothstep(0.992, 1.0, hash12(floor(surfaceWorldPosition.xz * 1.9) + floor(WorldData.x * 0.04)));
            color += vec3(0.62, 0.12, 0.025) * ember * VisualSurface.z * decorationMotion * 0.20;
        }
    }

    // Warm daylight and cool moonlight share the existing pass. Raw skylight at the
    // player is smoothed on the CPU so the tint fades naturally below roofs and in caves.
    if (Celestial.x > 0.5 && Celestial.z > 0.5 && depth > 0.000001) {
        float daylight = ScreenTime.w;
        float weatherVisibility = 1.0 - WeatherPoster.x * 0.28 - WeatherPoster.y * 0.30;
        float sunAmount = smoothstep(0.42, 0.76, daylight) * SunLight.w * weatherVisibility;
        float moonAmount = smoothstep(0.52, 0.90, 1.0 - daylight) * MoonLight.w
                * (1.0 - WeatherPoster.x * 0.16);
        float exposure = smoothstep(0.04, 0.82, Celestial.y);
        sunAmount *= exposure;
        moonAmount *= exposure;

        vec3 celestialColor = SunLight.rgb * sunAmount + MoonLight.rgb * moonAmount;
        float total = clamp(sunAmount + moonAmount, 0.0, 1.0);
        vec3 normalizedTint = celestialColor / max(luma(celestialColor), 0.001);
        color *= mix(vec3(1.0), normalizedTint, total * 0.16);
        color = 1.0 - (1.0 - color) * (1.0 - celestialColor * 0.075);
        float moonShadow = (1.0 - smoothstep(0.06, 0.36, luma(color))) * moonAmount;
        color = mix(color, color * vec3(0.90, 0.96, 1.0), moonShadow * VisualSurface.w);
    }

    if (Celestial.w > 0.5) {
        float originalLuma = luma(color);
        float chroma = sat(color);
        float vibranceMask = 1.0 - smoothstep(0.18, 0.72, chroma);
        float saturation = max(ColorGrade.x, 0.0)
                * (1.0 + ColorGrade.y * vibranceMask);
        color = mix(vec3(originalLuma), color, saturation);
        color *= ColorGrade.w;
        float temperature = ColorGrade.z;
        color *= vec3(1.0 + temperature * 0.12,
                      1.0 + abs(temperature) * 0.018,
                      1.0 - temperature * 0.12);
        float shadowMask = 1.0 - smoothstep(0.04, 0.42, originalLuma);
        color += vec3(ToneGrade.x * shadowMask);
    }

    if (ExposureData.y > 0.001) {
        vec3 exposed = color * mix(1.0, ExposureData.x, ExposureData.y);
        exposed /= 1.0 + max(exposed - vec3(1.0), vec3(0.0)) * 0.72;
        color = mix(color, exposed, ExposureData.y);
    }
    // Compress only values near the display ceiling; midtones and the user's
    // scene brightness remain unchanged.
    if (ToneGrade.y > 0.001) {
        vec3 excess = max(color - vec3(0.82), vec3(0.0));
        color = vec3(0.82) + (color - vec3(0.82)) / (vec3(1.0) + excess * ToneGrade.y * 2.4);
        color = max(color, vec3(0.0));
    }
    // Comfort pass: preserve shadow detail, keep saturated colours out of the
    // near-black range, and soften the display extremes without reducing the
    // user's overall brightness setting.
    float comfortLuma = luma(color);
    float shadowChromaMask = 1.0 - smoothstep(0.04, 0.30, comfortLuma);
    vec3 neutral = vec3(comfortLuma);
    color = mix(color, neutral, shadowChromaMask * Comfort.x);
    float whiteMask = smoothstep(0.74, 1.18, comfortLuma);
    color = mix(color, vec3(luma(color)), whiteMask * Comfort.y * 0.42);
    float blackMask = 1.0 - smoothstep(0.015, 0.16, comfortLuma);
    color += vec3(Comfort.z * blackMask * 0.055);
    if (Comfort.w > 0.001) {
        color = mix(color, sqrt(max(color, vec3(0.0))), Comfort.w * 0.16);
        color = mix(color, vec3(0.88) + (color - vec3(0.88)) / (1.0 + max(color - vec3(0.88), vec3(0.0)) * 1.8), Comfort.w * 0.30);
    }
    if (Flags0.x > 0.5) {
        float levels = max(WeatherPoster.z, 3.0);
        float noise = fract(sin(dot(uv * size + vec2(WorldData.x * 0.071), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        vec3 dithered = color + vec3(noise * ToneGrade.w * 0.32 / levels);
        vec3 quantized = floor(dithered * levels + 0.5) / levels;
        // The sky disc already contains a smooth horizon-to-zenith gradient.
        // Quantizing clear-depth pixels turns it into visible color bands, so
        // posterization is retained for lit world geometry but faded out on
        // sky and in shadows to preserve dark detail without extra contrast.
        float worldGeometry = smoothstep(0.000001, 0.00002, depth);
        float shadowDetail = smoothstep(0.07, 0.30, luma(color));
        color = mix(color, quantized, WeatherPoster.w * worldGeometry * shadowDetail);
    }
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
