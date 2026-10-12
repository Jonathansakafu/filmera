package com.filmera.app.core.filter

import com.filmera.app.core.filter.FilterCategory.BEAUTY
import com.filmera.app.core.filter.FilterCategory.COLOR
import com.filmera.app.core.filter.FilterCategory.FACE
import com.filmera.app.core.filter.FilterCategory.FUN
import com.filmera.app.core.filter.FilterCategory.LIGHTING
import com.filmera.app.core.filter.PerformanceLevel.HEAVY
import com.filmera.app.core.filter.PerformanceLevel.MEDIUM

/**
 * Every filter in the app. To add one, append a [FilterDefinition] here; the carousel,
 * categories and renderer pick it up automatically.
 */
object FilterCatalog {

    val ORIGINAL = FilterDefinition(
        id = "original", name = "Original", category = COLOR,
        description = "Your camera, untouched.",
        shader = "return cam(uv);",
        swatch = 0xFF3A3F47 to 0xFF1C1F24,
    )

    val all: List<FilterDefinition> = listOf(
        ORIGINAL,

        // ---------------------------------------------------------------- COLOR
        FilterDefinition(
            id = "warm", name = "Warm", category = COLOR,
            description = "Golden-hour warmth.",
            shader = """
                vec3 c = cam(uv) * vec3(1.08, 1.0, 0.86) + vec3(0.025, 0.01, 0.0);
                return saturate3(c, 1.08);
            """,
            swatch = 0xFFFFB46B to 0xFFE0643B,
        ),
        FilterDefinition(
            id = "cool", name = "Cool", category = COLOR,
            description = "Crisp, clean blue tones.",
            shader = """
                vec3 c = cam(uv) * vec3(0.9, 1.0, 1.1) + vec3(0.0, 0.01, 0.03);
                return saturate3(c, 1.05);
            """,
            swatch = 0xFF7FD3FF to 0xFF3B6BE0,
        ),
        FilterDefinition(
            id = "cinema", name = "Cinema", category = COLOR,
            description = "Teal shadows, warm highlights, soft vignette.",
            shader = """
                vec3 c = cam(uv);
                float l = luma(c);
                c = mix(c * vec3(0.82, 1.0, 1.1), c * vec3(1.12, 1.0, 0.82), smoothstep(0.15, 0.85, l));
                c = contrast3(c, 1.12);
                return c * vignette(uv, 0.9);
            """,
            swatch = 0xFF1FA2A6 to 0xFFF29A4A,
        ),
        FilterDefinition(
            id = "vintage", name = "Vintage", category = COLOR,
            description = "Faded film with grain.",
            animated = true,
            shader = """
                vec3 c = cam(uv);
                vec3 sepia = vec3(dot(c, vec3(0.393, 0.769, 0.189)),
                                  dot(c, vec3(0.349, 0.686, 0.168)),
                                  dot(c, vec3(0.272, 0.534, 0.131)));
                c = mix(c, sepia, 0.55);
                c = c * 0.9 + 0.07;
                c += grain(uv) * 0.06;
                return c * vignette(uv, 1.1);
            """,
            swatch = 0xFFD9B98A to 0xFF8A6A45,
        ),
        FilterDefinition(
            id = "bw", name = "B&W", category = COLOR,
            description = "Classic black and white.",
            shader = "return vec3(contrast3(vec3(luma(cam(uv))), 1.1));",
            swatch = 0xFFE6E6E6 to 0xFF2A2A2A,
        ),
        FilterDefinition(
            id = "noir", name = "Noir", category = COLOR,
            description = "High-contrast monochrome.",
            animated = true,
            shader = """
                float l = smoothstep(0.12, 0.88, luma(cam(uv)));
                l += grain(uv) * 0.05;
                return vec3(l) * vignette(uv, 1.4);
            """,
            swatch = 0xFF8C8C8C to 0xFF000000,
        ),
        FilterDefinition(
            id = "duotone", name = "Duotone", category = COLOR,
            description = "Two-color poster look.",
            shader = """
                float l = smoothstep(0.05, 0.95, luma(cam(uv)));
                return mix(vec3(0.12, 0.05, 0.38), vec3(1.0, 0.58, 0.38), l);
            """,
            swatch = 0xFFFF9461 to 0xFF1F0D61,
        ),
        FilterDefinition(
            id = "dreamy", name = "Dreamy", category = COLOR,
            description = "Soft pastel glow.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 6.0);
                c = 1.0 - (1.0 - c) * (1.0 - b * 0.55);
                c = mix(c, c * vec3(1.04, 0.97, 1.05), 0.8);
                return saturate3(c, 0.9);
            """,
            swatch = 0xFFFFC6E5 to 0xFFB59CFF,
        ),

        // ------------------------------------------------------------- LIGHTING
        FilterDefinition(
            id = "glow", name = "Glow", category = LIGHTING,
            description = "Soft light that flatters skin.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 4.0);
                vec3 screen = 1.0 - (1.0 - c) * (1.0 - b);
                return mix(c, screen, 0.45) + 0.02;
            """,
            swatch = 0xFFFFE7B0 to 0xFFFFA36B,
        ),
        FilterDefinition(
            id = "studio", name = "Studio", category = LIGHTING,
            description = "Brighter, punchier, clearer.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 1.5);
                c += (c - b) * 0.6;
                c = contrast3(c, 1.1) + 0.035;
                return saturate3(c, 1.08);
            """,
            swatch = 0xFFFFFFFF to 0xFF9AA4B2,
        ),
        FilterDefinition(
            id = "lowlight", name = "Low Light", category = LIGHTING,
            description = "Lifts dark scenes and calms noise.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 1.5);
                float dark = 1.0 - smoothstep(0.0, 0.5, luma(c));
                c = mix(c, b, 0.6 * dark);
                c = pow(max(c, 0.0), vec3(0.62));
                return saturate3(c, 1.12);
            """,
            swatch = 0xFF4B5BD6 to 0xFF101233,
        ),
        FilterDefinition(
            id = "hdr", name = "HDR Look", category = LIGHTING,
            description = "Boosted local detail and contrast.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 8.0);
                c = c + (c - b) * 1.1;
                c = c / (c + 0.35) * 1.35;
                return saturate3(c, 1.15);
            """,
            swatch = 0xFFFFD84D to 0xFF2E7D32,
        ),

        // --------------------------------------------------------------- BEAUTY
        FilterDefinition(
            id = "smooth", name = "Skin Smooth", category = BEAUTY,
            description = "Edge-aware smoothing that keeps eyes and hair sharp.",
            performance = HEAVY,
            shader = """
                vec3 c = cam(uv);
                vec3 sum = c;
                float wsum = 1.0;
                for (int i = 0; i < 12; i++) {
                    float fi = float(i);
                    float a = fi * 0.5236;
                    float r = mod(fi, 2.0) < 0.5 ? 3.5 : 7.0;
                    vec3 s = cam(uv + vec2(cos(a), sin(a)) * uTexel * r);
                    vec3 d = s - c;
                    float w = exp(-dot(d, d) * 60.0);
                    sum += s * w;
                    wsum += w;
                }
                vec3 smoothC = sum / wsum;
                return smoothC * vec3(1.02, 1.01, 1.0) + 0.015;
            """,
            swatch = 0xFFFFD1BF to 0xFFE89A86,
        ),
        FilterDefinition(
            id = "tone", name = "Face Tone", category = BEAUTY,
            description = "Even, warm skin tone with soft light.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 3.0);
                c = mix(c, b, 0.35);
                c = c * vec3(1.05, 1.0, 0.95) + vec3(0.03, 0.015, 0.01);
                return saturate3(c, 0.95);
            """,
            swatch = 0xFFF5C49C to 0xFFC27B5A,
        ),
        FilterDefinition(
            id = "detail", name = "Detail", category = BEAUTY,
            description = "Sharpens fine detail.",
            performance = MEDIUM,
            shader = """
                vec3 c = cam(uv);
                vec3 b = blur9(uv, 1.25);
                return c + (c - b) * 1.4;
            """,
            swatch = 0xFFCFE8FF to 0xFF5C7A99,
        ),

        // ----------------------------------------------------------------- FACE
        FilterDefinition(
            id = "bigeyes", name = "Big Eyes", category = FACE,
            description = "Cartoon-sized eyes that follow your face.",
            requiresFaceTracking = true, blendWithOriginal = false,
            shader = """
                if (uFace < 0.5) return cam(uv);
                float ed = eyeDistance();
                float s = 0.38 * uIntensity;
                vec2 p = bulge(uv, uEyeA, 0.5 * ed, s);
                p = bulge(p, uEyeB, 0.5 * ed, s);
                return cam(p);
            """,
            swatch = 0xFF8EF0FF to 0xFF3D8BFF,
        ),
        FilterDefinition(
            id = "alien", name = "Alien", category = FACE,
            description = "Huge eyes, tiny mouth, green skin.",
            requiresFaceTracking = true, blendWithOriginal = false,
            shader = """
                if (uFace < 0.5) return cam(uv);
                float ed = eyeDistance();
                vec2 p = bulge(uv, uEyeA, 0.62 * ed, 0.55 * uIntensity);
                p = bulge(p, uEyeB, 0.62 * ed, 0.55 * uIntensity);
                p = bulge(p, uMouth, 0.75 * ed, -0.7 * uIntensity);
                vec3 c = cam(p);
                vec2 center = (uForehead + uChin) * 0.5;
                vec2 q = faceLocal(uv, center);
                float h = length(toAspect(uChin) - toAspect(uForehead)) / ed;
                float e = length(q / vec2(h * 0.42, h * 0.55));
                float mask = 1.0 - smoothstep(0.85, 1.05, e);
                vec3 green = vec3(luma(c)) * vec3(0.55, 1.05, 0.6) + vec3(0.0, 0.06, 0.0);
                return mix(c, green, mask * 0.75 * uIntensity);
            """,
            swatch = 0xFF9BFF7A to 0xFF1E8C3A,
        ),
        FilterDefinition(
            id = "shades", name = "Shades", category = FACE,
            description = "Glossy sunglasses locked to your eyes.",
            requiresFaceTracking = true,
            shader = """
                vec3 c = cam(uv);
                if (uFace < 0.5) return c;
                vec2 mid = (uEyeA + uEyeB) * 0.5;
                vec2 q = faceLocal(uv, mid);
                vec2 lens = vec2(abs(q.x) - 0.5, q.y - 0.06);
                float d = length(lens / vec2(0.40, 0.30));
                float bridge = step(abs(q.x), 0.12) * (1.0 - smoothstep(0.025, 0.04, abs(q.y + 0.08)));
                float frame = (1.0 - smoothstep(1.0, 1.04, d)) * smoothstep(0.86, 0.9, d);
                float glass = 1.0 - smoothstep(0.94, 0.98, d);
                vec3 tint = mix(vec3(0.03, 0.05, 0.08), vec3(0.16, 0.22, 0.3), clamp(0.5 - lens.y * 1.5, 0.0, 1.0));
                float shine = smoothstep(0.03, 0.0, abs(lens.x + lens.y * 0.8 + 0.12)) * 0.5;
                vec3 g = tint + c * 0.12 + shine;
                c = mix(c, g, glass);
                c = mix(c, vec3(0.02), max(frame, bridge));
                return c;
            """,
            swatch = 0xFF4A5560 to 0xFF0B0F14,
        ),
        FilterDefinition(
            id = "clown", name = "Clown Nose", category = FACE,
            description = "A shiny red nose.",
            requiresFaceTracking = true,
            shader = """
                vec3 c = cam(uv);
                if (uFace < 0.5) return c;
                vec2 q = faceLocal(uv, uNose) / 0.3;
                float d = length(q);
                if (d > 1.02) return c;
                float z = sqrt(max(0.0, 1.0 - d * d));
                vec3 n = vec3(q, z);
                float light = clamp(dot(n, normalize(vec3(-0.4, -0.5, 0.75))), 0.0, 1.0);
                float spec = pow(light, 24.0);
                vec3 red = vec3(0.85, 0.05, 0.08) * (0.35 + 0.65 * light) + spec * 0.8;
                return mix(c, red, 1.0 - smoothstep(0.97, 1.02, d));
            """,
            swatch = 0xFFFF5A5F to 0xFFB0001A,
        ),
        FilterDefinition(
            id = "blush", name = "Blush", category = FACE,
            description = "Rosy cheeks and a soft glow.",
            requiresFaceTracking = true,
            performance = MEDIUM,
            shader = """
                vec3 c = mix(cam(uv), blur9(uv, 2.5), 0.25);
                if (uFace < 0.5) return c;
                float ed = eyeDistance();
                float da = length(toAspect(uv) - toAspect(uCheekA)) / (0.38 * ed);
                float db = length(toAspect(uv) - toAspect(uCheekB)) / (0.38 * ed);
                float m = (1.0 - smoothstep(0.0, 1.0, da)) + (1.0 - smoothstep(0.0, 1.0, db));
                vec3 rosy = c * vec3(1.0, 0.72, 0.76) + vec3(0.1, 0.0, 0.03);
                return mix(c, rosy, clamp(m, 0.0, 1.0) * 0.55);
            """,
            swatch = 0xFFFFB3C7 to 0xFFE85D8A,
        ),
        FilterDefinition(
            id = "hearts", name = "Hearts", category = FACE,
            description = "Hearts orbiting your head.",
            requiresFaceTracking = true, animated = true,
            shader = """
                vec3 c = cam(uv);
                if (uFace < 0.5) return c;
                vec2 crown = uForehead;
                for (int i = 0; i < 5; i++) {
                    float a = uTime * 1.3 + float(i) * 1.2566;
                    vec2 local = vec2(cos(a) * 1.5, sin(a) * 0.35 - 1.05);
                    vec2 q = faceLocal(uv, crown) - local;
                    float size = 0.2 + 0.05 * sin(uTime * 3.0 + float(i));
                    q /= size;
                    float x = q.x;
                    float y = -q.y + 0.25;
                    float t = x * x + y * y - 1.0;
                    float f = t * t * t - x * x * y * y * y;
                    if (f < 0.0 && sin(a) > -0.2) {
                        vec3 pink = mix(vec3(1.0, 0.25, 0.45), vec3(1.0, 0.75, 0.85), clamp(-q.y * 0.5 + 0.2, 0.0, 1.0));
                        c = pink;
                    }
                }
                return c;
            """,
            swatch = 0xFFFF8FB1 to 0xFFFF2D6F,
        ),

        // ------------------------------------------------------------------ FUN
        FilterDefinition(
            id = "fisheye", name = "Fisheye", category = FUN,
            description = "Wide, bulging lens.",
            blendWithOriginal = false,
            shader = """
                vec2 center = toAspect(vec2(0.5));
                vec2 d = toAspect(uv) - center;
                float k = 0.85 * uIntensity;
                vec2 p = center + d * (1.0 - k + k * length(d) / 0.55);
                return cam(p / vec2(uAspect, 1.0)) * vignette(uv, 0.6);
            """,
            swatch = 0xFF6BF2D2 to 0xFF2667FF,
        ),
        FilterDefinition(
            id = "mirror", name = "Mirror", category = FUN,
            description = "Perfectly symmetric reflection.",
            blendWithOriginal = false,
            shader = "return cam(vec2(uv.x < 0.5 ? uv.x : 1.0 - uv.x, uv.y));",
            swatch = 0xFFC0C8D0 to 0xFF59636E,
        ),
        FilterDefinition(
            id = "comic", name = "Comic", category = FUN,
            description = "Bold ink lines and flat color.",
            performance = MEDIUM,
            shader = """
                vec3 c = blur9(uv, 1.0);
                c = floor(c * 5.0 + 0.5) / 5.0;
                float e = smoothstep(0.25, 0.6, edges(uv, 1.5));
                return saturate3(c, 1.3) * (1.0 - e);
            """,
            swatch = 0xFFFFE44D to 0xFFFF3D3D,
        ),
        FilterDefinition(
            id = "sketch", name = "Sketch", category = FUN,
            description = "Pencil drawing on paper.",
            performance = MEDIUM,
            shader = """
                float e = edges(uv, 1.2);
                float l = 1.0 - smoothstep(0.08, 0.5, e);
                l *= mix(0.92, 1.0, luma(cam(uv)));
                return vec3(l) * vec3(0.98, 0.96, 0.9);
            """,
            swatch = 0xFFF7F2E6 to 0xFF8C8577,
        ),
        FilterDefinition(
            id = "thermal", name = "Thermal", category = FUN,
            description = "Heat-camera colors.",
            shader = """
                float l = luma(cam(uv));
                vec3 a = vec3(0.05, 0.0, 0.3);
                vec3 b = vec3(0.6, 0.0, 0.6);
                vec3 c = vec3(1.0, 0.25, 0.0);
                vec3 d = vec3(1.0, 0.95, 0.3);
                vec3 col = mix(a, b, smoothstep(0.0, 0.33, l));
                col = mix(col, c, smoothstep(0.33, 0.66, l));
                return mix(col, d, smoothstep(0.66, 1.0, l));
            """,
            swatch = 0xFFFFD23F to 0xFF5A00A0,
        ),
        FilterDefinition(
            id = "glitch", name = "Glitch", category = FUN,
            description = "Digital RGB split.",
            animated = true,
            shader = """
                float band = floor(uv.y * 28.0);
                float t = floor(uTime * 8.0);
                float jump = step(0.86, hash(vec2(band, t))) * (hash(vec2(t, band)) - 0.5) * 0.08;
                vec2 p = uv + vec2(jump, 0.0);
                float split = 0.006 + 0.01 * step(0.9, hash(vec2(t, 3.0)));
                float r = cam(p + vec2(split, 0.0)).r;
                float g = cam(p).g;
                float b = cam(p - vec2(split, 0.0)).b;
                return vec3(r, g, b);
            """,
            swatch = 0xFFFF2EC4 to 0xFF00E5FF,
        ),
        FilterDefinition(
            id = "pixel", name = "Pixel", category = FUN,
            description = "Retro 8-bit blocks.",
            blendWithOriginal = false,
            shader = """
                float cells = mix(180.0, 48.0, uIntensity);
                vec2 grid = vec2(cells * uAspect, cells);
                vec2 p = (floor(uv * grid) + 0.5) / grid;
                return floor(cam(p) * 8.0 + 0.5) / 8.0;
            """,
            swatch = 0xFF7CFF6B to 0xFF0D6E3F,
        ),
        FilterDefinition(
            id = "vhs", name = "VHS", category = FUN,
            description = "Tape wobble, bleed and scanlines.",
            animated = true,
            shader = """
                float wobble = sin(uv.y * 90.0 + uTime * 12.0) * 0.0015;
                vec2 p = uv + vec2(wobble, 0.0);
                vec3 c = vec3(cam(p + vec2(0.004, 0.0)).r, cam(p).g, cam(p - vec2(0.003, 0.0)).b);
                c *= 0.9 + 0.1 * sin(uv.y / uTexel.y * 1.6);
                c += grain(uv) * 0.08;
                return saturate3(c, 0.85) * vignette(uv, 0.8);
            """,
            swatch = 0xFF6E7BFF to 0xFF2B0F4C,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String?): FilterDefinition = byId[id] ?: ORIGINAL

    fun inCategory(category: FilterCategory) = all.filter { it.category == category }
}
