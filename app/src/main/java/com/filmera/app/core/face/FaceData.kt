package com.filmera.app.core.face

/**
 * Key face points in image space (upright, un-mirrored, 0..1, top-left origin).
 * Each point is a 2-element [x, y] array. [eyeA] is always the eye with the smaller x.
 */
class FaceData(
    val eyeA: FloatArray,
    val eyeB: FloatArray,
    val nose: FloatArray,
    val mouth: FloatArray,
    val cheekA: FloatArray,
    val cheekB: FloatArray,
    val forehead: FloatArray,
    val chin: FloatArray,
) {
    fun points() = listOf(eyeA, eyeB, nose, mouth, cheekA, cheekB, forehead, chin)

    companion object {
        // MediaPipe Face Landmarker indices (478-point mesh with irises).
        const val IRIS_1 = 468
        const val IRIS_2 = 473
        const val NOSE_TIP = 1
        const val LIP_UPPER_INNER = 13
        const val LIP_LOWER_INNER = 14
        const val CHEEK_1 = 50
        const val CHEEK_2 = 280
        const val FOREHEAD = 10
        const val CHIN = 152
        const val REQUIRED_POINTS = 478

        /** Builds FaceData from a landmark lookup `(index) -> [x, y]`. */
        fun fromLandmarks(point: (Int) -> FloatArray): FaceData {
            val i1 = point(IRIS_1)
            val i2 = point(IRIS_2)
            val c1 = point(CHEEK_1)
            val c2 = point(CHEEK_2)
            val (eyeA, eyeB) = if (i1[0] <= i2[0]) i1 to i2 else i2 to i1
            val (cheekA, cheekB) = if (c1[0] <= c2[0]) c1 to c2 else c2 to c1
            val up = point(LIP_UPPER_INNER)
            val low = point(LIP_LOWER_INNER)
            return FaceData(
                eyeA = eyeA, eyeB = eyeB,
                nose = point(NOSE_TIP),
                mouth = floatArrayOf((up[0] + low[0]) / 2, (up[1] + low[1]) / 2),
                cheekA = cheekA, cheekB = cheekB,
                forehead = point(FOREHEAD),
                chin = point(CHIN),
            )
        }
    }
}

/**
 * Smooths landmark jitter with a speed-adaptive exponential filter: slow movements are
 * smoothed heavily (no shimmer when you hold still), fast movements pass through quickly
 * (effects don't lag behind your head).
 */
class LandmarkSmoother(
    private val minAlpha: Float = 0.25f,
    private val maxAlpha: Float = 0.85f,
    /** Movement (in image fractions per update) at which alpha reaches max. */
    private val fastMotion: Float = 0.03f,
) {
    private var last: FaceData? = null

    fun reset() {
        last = null
    }

    fun smooth(next: FaceData): FaceData {
        val prev = last ?: return next.also { last = it }
        val prevPts = prev.points()
        val nextPts = next.points()
        var motion = 0f
        for (i in prevPts.indices) {
            motion = maxOf(motion, kotlin.math.hypot(nextPts[i][0] - prevPts[i][0], nextPts[i][1] - prevPts[i][1]))
        }
        val alpha = minAlpha + (maxAlpha - minAlpha) * (motion / fastMotion).coerceIn(0f, 1f)
        fun mix(p: FloatArray, n: FloatArray) =
            floatArrayOf(p[0] + (n[0] - p[0]) * alpha, p[1] + (n[1] - p[1]) * alpha)
        val out = FaceData(
            mix(prev.eyeA, next.eyeA), mix(prev.eyeB, next.eyeB),
            mix(prev.nose, next.nose), mix(prev.mouth, next.mouth),
            mix(prev.cheekA, next.cheekA), mix(prev.cheekB, next.cheekB),
            mix(prev.forehead, next.forehead), mix(prev.chin, next.chin),
        )
        last = out
        return out
    }
}
