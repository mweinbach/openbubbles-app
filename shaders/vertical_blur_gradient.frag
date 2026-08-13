#include <flutter/runtime_effect.glsl>

uniform sampler2D uTexture;
uniform vec2 uImageResolution;
uniform float uPadding;
uniform float uEncroachDistance;

uniform vec3 uBackgroundColor;

out vec4 frag_color;

vec2 mirror(vec2 uv) {
    uv = abs(mod(uv, 2.0));
    return mix(uv, 2.0 - uv, step(1.0, uv));
}

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

vec3 saturation(vec3 rgb, float adjustment)
{
    // Algorithm from Chapter 16 of OpenGL Shading Language
    const vec3 W = vec3(0.2125, 0.7154, 0.0721);
    vec3 intensity = vec3(dot(rgb, W));
    return mix(intensity, rgb, adjustment);
}

void main() {

    vec2 coords = gl_FragCoord.xy - vec2(0, uPadding);

    float distance = coords.y / uEncroachDistance;
    float maxBlur = 20;
    float blur = mix(0.0, maxBlur, clamp(1.0 - distance, 0.0, 1.0)); // 0px at bottom, 100px at top
    
    vec2 uv = coords / uImageResolution;

    vec4 color = vec4(0.0);
    float total = 0.0;
    float sigma = blur * 0.5;
    float sigmaPre = (2.0 * sigma * sigma);
    float scale = uImageResolution.x / 500;

    const int kernelRadius = 18;
    for (int x = -kernelRadius; x <= kernelRadius; ++x)
    {
        for (int y = -kernelRadius; y <= kernelRadius; ++y)
        {
            vec2 offset = vec2(float(x), float(y)) / uImageResolution;
            float dist = length(vec2(float(x), float(y)));
            float weight = exp(-(dist * dist) / sigmaPre);

            color += texture(uTexture, mirror(uv + offset * scale)) * weight;
            total += weight;
        }
    }

    vec4 blurredColor = color / total;

    float raw = (gl_FragCoord.y - (uPadding / 3)) / ((uPadding / 3 * 2) + uEncroachDistance);
    float finalOpacity = smoothstep(0.0, 1.0, raw);

    vec4 saturated = vec4(saturation(blurredColor.xyz, 1 + 1 - finalOpacity), 1.0);

    frag_color = mix(mix(vec4(uBackgroundColor, 1.0), saturated, 0.05), saturated, finalOpacity);
}