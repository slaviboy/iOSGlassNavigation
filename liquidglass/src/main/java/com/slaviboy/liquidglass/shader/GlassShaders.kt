/*
* Copyright (C) 2022 Stanislav Georgiev
* https://github.com/slaviboy
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.slaviboy.liquidglass.shader

/*
 * AGSL programs of the Full render tier (API 33+). Coordinates are layer pixels; every program
 * receives the backdrop (already blurred and saturated by the RenderEffect chain) as `content`.
 *
 * Optics: a convex glass slab bends the light at its rounded bezel. The bezel profile is the sag
 * of a circular arc, `1 - sqrt(1 - u^2)`, measured from UIKit. The outward normal is the gradient
 * of the rounded-rectangle SDF blended toward the radial direction ("ovalization").
 */

private const val COMMON = """
const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float2 gradRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    float2 g;
    if (q.x > 0.0 && q.y > 0.0) {
        g = normalize(q);
    } else if (q.x > q.y) {
        g = float2(1.0, 0.0);
    } else {
        g = float2(0.0, 1.0);
    }
    return g * float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
}

float2 outN(float2 p, float2 b, float r, float oval) {
    float2 g = gradRR(p, b, r);
    float2 o = p / max(b, float2(1.0));
    float lo = length(o);
    o = lo > 0.0001 ? o / lo : g;
    float2 n = mix(g, o, oval);
    float ln = length(n);
    return ln > 0.0001 ? n / ln : g;
}

float sag(float depth, float h) {
    float u = clamp(1.0 - depth / max(h, 0.0001), 0.0, 1.0);
    return 1.0 - sqrt(max(1.0 - u * u, 0.0));
}

float3 vibrant(float3 c, float3 m) {
    float l = dot(c, LUMA);
    return clamp(m.x * (c - float3(l)) + float3(m.y * l + m.z), 0.0, 1.0);
}

float3 straightColor(float4 s) {
    return s.a > 0.0001 ? s.rgb / s.a : float3(0.0);
}

float3 wash(float3 c, float press) {
    float l = dot(c, LUMA);
    return c + 0.8448 * press * (0.2 * (c - float3(l)) + float3(0.05));
}

float3 rimLight(float3 c, float2 n, float depth, float2 lightDir, float4 rim, float3 rimMat, float specular) {
    float w = max(rim.x, 0.0001);
    float band = 1.0 - smoothstep(0.0, w, depth);
    float nl = abs(dot(n, lightDir));
    float a = nl * (1.0 - rim.y * clamp(depth / w, 0.0, 1.0)) * rim.z * band;
    c = mix(c, vibrant(c, rimMat), clamp(a, 0.0, 1.0));
    return c + float3(pow(max(nl, 0.0001), rim.w) * band * specular);
}
"""

/** The bar ("platter"): refraction at the bezel, frosted face, tint, press wash, lit rim. */
internal const val PLATTER_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uHalf;
uniform float uRadius;
uniform float4 uClamp;
uniform float2 uRefract;
uniform float uOval;
uniform float4 uMap;
uniform float2 uFill;
uniform float2 uFrost;
uniform float4 uTint;
uniform float uDim;
uniform float2 uLight;
uniform float4 uRim;
uniform float3 uRimMat;
uniform float uSpecular;
uniform float uPress;
$COMMON
half4 main(float2 coord) {
    float2 p = coord - uCenter;
    float d = sdRR(p, uHalf, uRadius);
    float cov = clamp(0.5 - d, 0.0, 1.0);
    if (cov <= 0.0) {
        return half4(0.0);
    }
    float depth = max(-d, 0.0);
    float2 n = outN(p, uHalf, uRadius, uOval);

    // Convex bezel: the rim shows the backdrop pulled in from deeper inside the glass.
    float2 q = coord - n * (uRefract.x * sag(depth, uRefract.y));
    float4 s = float4(content.eval(clamp(q, uClamp.xy, uClamp.zw)));
    float3 c = straightColor(s);

    c = wash(c, uPress);
    // UIKit face: the luma is remapped into [black, white] (which follows the average brightness
    // behind the glass) while the chroma is kept, so colors stay vivid but light glass stays light.
    float l = dot(c, LUMA);
    c = float3(mix(uMap.x, uMap.y, l)) + (c - float3(l)) * uMap.z;
    // Tint is mapped to the brightness of the content underneath, like colored glass.
    c = mix(c, uTint.rgb * mix(0.8, 1.2, l), uTint.a);
    // Frosted fill: thinner at the rim, full strength deeper in.
    float frost = mix(uFrost.x, 1.0, smoothstep(0.0, max(uFrost.y, 0.0001), depth));
    c = mix(c, float3(uFill.x), clamp(uFill.y * frost, 0.0, 1.0));
    c *= 1.0 - uDim;
    c = rimLight(c, n, depth, uLight, uRim, uRimMat, uSpecular);

    float a = cov * clamp(s.a, 0.0, 1.0);
    return half4(half3(clamp(c, 0.0, 1.0) * a), half(a));
}
"""

/**
 * The lifted droplet: inner refraction at the rim, outward warp across the body, 7-tap chromatic
 * dispersion near the edge, tone, top shade, press wash and lit rim.
 */
internal const val LENS_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uHalf;
uniform float uRadius;
uniform float4 uClamp;
uniform float uOval;
uniform float2 uInner;
uniform float2 uWarp;
uniform float2 uAberr;
uniform float4 uAberrMat;
uniform float uTone;
uniform float2 uTopShade;
uniform float4 uFace;
uniform float2 uLight;
uniform float4 uRim;
uniform float3 uRimMat;
uniform float uSpecular;
uniform float uPress;
uniform float uAlpha;
$COMMON
half4 main(float2 coord) {
    float2 p = coord - uCenter;
    float d = sdRR(p, uHalf, uRadius);
    float cov = clamp(0.5 - d, 0.0, 1.0) * uAlpha;
    if (cov <= 0.0) {
        return half4(0.0);
    }
    float depth = max(-d, 0.0);
    float2 n = outN(p, uHalf, uRadius, uOval);

    float2 q = coord - n * (uInner.x * sag(depth, uInner.y)) + n * (uWarp.x * sag(depth, uWarp.y));
    float e = 1.0 - smoothstep(0.0, max(uAberr.y, 0.0001), depth);
    float4 s;
    if (uAberr.x > 0.0 && e > 0.001) {
        float2x2 m = float2x2(uAberrMat.x, uAberrMat.y, uAberrMat.z, uAberrMat.w);
        float2 v = (m * (p / max(uHalf, float2(1.0)))) * (uAberr.x * e);
        float4 t0 = float4(content.eval(clamp(q - v, uClamp.xy, uClamp.zw)));
        float4 t1 = float4(content.eval(clamp(q - v * 0.6666667, uClamp.xy, uClamp.zw)));
        float4 t2 = float4(content.eval(clamp(q - v * 0.3333333, uClamp.xy, uClamp.zw)));
        float4 t3 = float4(content.eval(clamp(q, uClamp.xy, uClamp.zw)));
        float4 t4 = float4(content.eval(clamp(q + v * 0.3333333, uClamp.xy, uClamp.zw)));
        float4 t5 = float4(content.eval(clamp(q + v * 0.6666667, uClamp.xy, uClamp.zw)));
        float4 t6 = float4(content.eval(clamp(q + v, uClamp.xy, uClamp.zw)));
        // Red from one side, blue from the other, green centered; each channel sums to 1.
        float r = (t4.r * 0.3333333 + t5.r * 0.6666667 + t6.r) * 0.5;
        float g = (t1.g * 0.3333333 + t2.g * 0.6666667 + t3.g + t4.g * 0.6666667 + t5.g * 0.3333333) * 0.3333333;
        float b = (t2.b * 0.3333333 + t1.b * 0.6666667 + t0.b) * 0.5;
        float a = (t0.a + t1.a + t2.a + t3.a + t4.a + t5.a + t6.a) * 0.1428571;
        s = float4(r, g, b, a);
    } else {
        s = float4(content.eval(clamp(q, uClamp.xy, uClamp.zw)));
    }
    float3 c = straightColor(s) * uTone;
    c = wash(c, uPress);
    c = mix(c, uFace.rgb, clamp(uFace.a, 0.0, 1.0));
    float top = max(-n.y, 0.0) * (1.0 - clamp(depth / max(uTopShade.y, 0.0001), 0.0, 1.0));
    c *= 1.0 - uTopShade.x * top;
    c = rimLight(c, n, depth, uLight, uRim, uRimMat, uSpecular);

    float a = cov * clamp(s.a, 0.0, 1.0);
    return half4(half3(clamp(c, 0.0, 1.0) * a), half(a));
}
"""
