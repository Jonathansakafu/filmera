package com.filmera.app.core.filter

enum class FilterCategory(val label: String) {
    COLOR("Color"),
    LIGHTING("Lighting"),
    BEAUTY("Beauty"),
    FACE("Face"),
    FUN("Fun"),
}

enum class PerformanceLevel { LIGHT, MEDIUM, HEAVY }

/**
 * One filter. The UI renders from these definitions, and the renderer compiles [shader]
 * into a GPU program the first time the filter is used.
 *
 * [shader] is the body of `vec3 effect(vec2 uv)`, written in GLSL ES 1.00. `uv` is in
 * image space (upright, un-mirrored, 0..1, top-left origin) and can call the helpers in
 * [FilterShaders.COMMON], e.g. `cam(uv)` to sample the camera.
 */
data class FilterDefinition(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val description: String,
    val shader: String,
    val requiresFaceTracking: Boolean = false,
    /**
     * When true, the intensity slider cross-fades between the original frame and the
     * effect. Warps set this to false and read `uIntensity` themselves, since fading a
     * warp produces ghosting.
     */
    val blendWithOriginal: Boolean = true,
    val performance: PerformanceLevel = PerformanceLevel.LIGHT,
    val animated: Boolean = false,
    /** Two ARGB colors used to draw the filter's chip in the carousel. */
    val swatch: Pair<Long, Long>,
)
