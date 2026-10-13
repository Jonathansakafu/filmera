package com.filmera.app.core.filter

object FilterShaders {

    const val VERTEX = """
attribute vec4 aPosition;
attribute vec2 aOutputUv;
uniform mat4 uOutputToImage;
varying vec2 vImageUv;
void main() {
    gl_Position = aPosition;
    vImageUv = (uOutputToImage * vec4(aOutputUv, 0.0, 1.0)).xy;
}
"""

    /** Uniforms and helpers shared by every filter. */
    const val COMMON = """
#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vImageUv;
uniform samplerExternalOES uTex;
uniform mat4 uImageToTex;
uniform vec2 uTexel;       // one image pixel, in uv units
uniform float uAspect;     // image width / height
uniform float uTime;       // seconds
uniform float uIntensity;  // 0..1
uniform vec4 uAdjust;      // brightness, contrast, saturation, warmth (each -1..1)

uniform float uFace;       // 1.0 when a face is tracked
uniform vec2 uEyeA;        // eye with the smaller x
uniform vec2 uEyeB;
uniform vec2 uNose;
uniform vec2 uMouth;
uniform vec2 uCheekA;
uniform vec2 uCheekB;
uniform vec2 uForehead;
uniform vec2 uChin;

vec3 cam(vec2 uv) {
    uv = clamp(uv, uTexel, 1.0 - uTexel);
    return texture2D(uTex, (uImageToTex * vec4(uv, 0.0, 1.0)).xy).rgb;
}

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

vec3 saturate3(vec3 c, float s) { return mix(vec3(luma(c)), c, s); }

vec3 contrast3(vec3 c, float k) { return (c - 0.5) * k + 0.5; }

float vignette(vec2 uv, float k) {
    vec2 d = uv - 0.5;
    return clamp(1.0 - k * dot(d, d) * 2.2, 0.0, 1.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float grain(vec2 uv) {
    return hash(floor(uv / uTexel) + fract(uTime) * 61.0) - 0.5;
}

vec3 blur9(vec2 uv, float radius) {
    vec2 o = uTexel * radius;
    vec3 s = cam(uv) * 0.25;
    s += (cam(uv + vec2(o.x, 0.0)) + cam(uv - vec2(o.x, 0.0))
        + cam(uv + vec2(0.0, o.y)) + cam(uv - vec2(0.0, o.y))) * 0.125;
    s += (cam(uv + o) + cam(uv - o)
        + cam(uv + vec2(o.x, -o.y)) + cam(uv + vec2(-o.x, o.y))) * 0.0625;
    return s;
}

float edges(vec2 uv, float radius) {
    vec2 o = uTexel * radius;
    float tl = luma(cam(uv + vec2(-o.x, -o.y)));
    float t  = luma(cam(uv + vec2(0.0, -o.y)));
    float tr = luma(cam(uv + vec2(o.x, -o.y)));
    float l  = luma(cam(uv + vec2(-o.x, 0.0)));
    float r  = luma(cam(uv + vec2(o.x, 0.0)));
    float bl = luma(cam(uv + vec2(-o.x, o.y)));
    float b  = luma(cam(uv + vec2(0.0, o.y)));
    float br = luma(cam(uv + vec2(o.x, o.y)));
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    return length(vec2(gx, gy));
}

/** 5-tap unsharp mask: cheap detail recovery for soft phone cameras. */
vec3 sharpen(vec2 uv, vec3 c, float amount) {
    vec3 n = cam(uv + vec2(uTexel.x, 0.0)) + cam(uv - vec2(uTexel.x, 0.0))
           + cam(uv + vec2(0.0, uTexel.y)) + cam(uv - vec2(0.0, uTexel.y));
    return c + (c - n * 0.25) * amount;
}

/** Gentle film-style S-curve; k = 0 is linear, 1 is a full smoothstep. */
vec3 filmic(vec3 c, float k) {
    c = clamp(c, 0.0, 1.0);
    return mix(c, c * c * (3.0 - 2.0 * c), k);
}

/** Soft highlight glow: blurred bright areas only. */
vec3 bloom(vec2 uv, float radius, float threshold) {
    vec3 b = blur9(uv, radius) * 0.5 + blur9(uv, radius * 2.2) * 0.5;
    return max(b - threshold, 0.0) / (1.0 - threshold);
}

/**
 * Likelihood (0..1) that a color is skin, from its chroma in YCbCr. Works across skin
 * tones because it ignores brightness. Used to keep beauty effects off hair, eyes and
 * backgrounds.
 */
float skinMask(vec3 c) {
    float cb = 0.5 - 0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b;
    float cr = 0.5 + 0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b;
    float m = smoothstep(0.29, 0.33, cb) * (1.0 - smoothstep(0.49, 0.53, cb))
            * smoothstep(0.51, 0.54, cr) * (1.0 - smoothstep(0.66, 0.70, cr));
    return m * smoothstep(0.08, 0.2, luma(c));
}

/** Film grain weighted toward mid-tones, where it looks natural. */
float filmGrain(vec2 uv, vec3 c) {
    float l = luma(c);
    return grain(uv) * (1.0 - abs(l - 0.5) * 1.6);
}

// ---- Face helpers. All face math runs in "aspect space" (x scaled by uAspect) so
// circles stay round on non-square images.

vec2 toAspect(vec2 uv) { return uv * vec2(uAspect, 1.0); }

float eyeDistance() { return length(toAspect(uEyeB) - toAspect(uEyeA)); }

vec2 faceAxisX() { return normalize(toAspect(uEyeB) - toAspect(uEyeA)); }

vec2 faceAxisY() { vec2 x = faceAxisX(); return vec2(-x.y, x.x); }

/** Local face coordinates around `origin`, in units of eye distance. */
vec2 faceLocal(vec2 uv, vec2 origin) {
    vec2 d = toAspect(uv) - toAspect(origin);
    return vec2(dot(d, faceAxisX()), dot(d, faceAxisY())) / eyeDistance();
}

/** Enlarges (s > 0) or shrinks (s < 0) the picture inside radius r around c. */
vec2 bulge(vec2 uv, vec2 c, float r, float s) {
    vec2 d = toAspect(uv) - toAspect(c);
    float dist = length(d);
    if (dist < r) {
        float p = dist / r;
        d *= 1.0 - s * (1.0 - p * p);
    }
    return (toAspect(c) + d) / vec2(uAspect, 1.0);
}

vec3 adjust(vec3 c) {
    c += uAdjust.x * 0.25;
    c = contrast3(c, 1.0 + uAdjust.y * 0.5);
    c = saturate3(c, 1.0 + uAdjust.z);
    c += vec3(uAdjust.w * 0.07, uAdjust.w * 0.015, -uAdjust.w * 0.07);
    return c;
}
"""

    fun fragment(filter: FilterDefinition): String {
        val main = if (filter.blendWithOriginal) {
            "vec3 col = mix(cam(vImageUv), effect(vImageUv), uIntensity);"
        } else {
            "vec3 col = effect(vImageUv);"
        }
        return buildString {
            append(COMMON)
            append("\nvec3 effect(vec2 uv) {\n")
            append(filter.shader.trimIndent())
            append("\n}\n\nvoid main() {\n    ")
            append(main)
            append("\n    gl_FragColor = vec4(clamp(adjust(col), 0.0, 1.0), 1.0);\n}\n")
        }
    }
}
