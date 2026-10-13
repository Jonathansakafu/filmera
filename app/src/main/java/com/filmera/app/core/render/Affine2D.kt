package com.filmera.app.core.render

/**
 * A 2D affine map: (u, v) -> (a*u + c*v + tx, b*u + d*v + ty).
 *
 * The renderer moves between three coordinate spaces, all normalized 0..1 with the
 * origin at the top-left and v pointing down ("image convention"):
 *
 *  - output space: pixels of the surface being drawn (screen, video encoder, photo FBO)
 *  - image space:  the upright, un-mirrored camera picture. Filters and face landmarks
 *                  live here, so effects line up no matter where the frame is going.
 *  - buffer space: the raw camera buffer, in sensor orientation.
 *
 * Keeping the maps as plain values (no Android types) lets them be unit tested on the JVM.
 */
data class Affine2D(
    val a: Float = 1f, val b: Float = 0f,
    val c: Float = 0f, val d: Float = 1f,
    val tx: Float = 0f, val ty: Float = 0f,
) {
    fun mapX(u: Float, v: Float) = a * u + c * v + tx
    fun mapY(u: Float, v: Float) = b * u + d * v + ty

    /** Returns the map that applies `this` first and then [next]. */
    fun then(next: Affine2D) = Affine2D(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        tx = next.a * tx + next.c * ty + next.tx,
        ty = next.b * tx + next.d * ty + next.ty,
    )

    /** The inverse map. Only valid for non-degenerate maps (all maps used here are). */
    fun inverse(): Affine2D {
        val det = a * d - b * c
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine2D(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }

    /** Column-major 4x4 matrix, for use as a GLSL `mat4` applied to vec4(u, v, 0, 1). */
    fun toMat4(): FloatArray = floatArrayOf(
        a, b, 0f, 0f,
        c, d, 0f, 0f,
        0f, 0f, 1f, 0f,
        tx, ty, 0f, 1f,
    )

    companion object {
        val IDENTITY = Affine2D()

        fun flipX() = Affine2D(a = -1f, tx = 1f)
        fun flipY() = Affine2D(d = -1f, ty = 1f)

        /**
         * Maps a point in a buffer to the same point after the buffer is rotated
         * [degrees] clockwise (0, 90, 180 or 270).
         */
        fun rotateClockwise(degrees: Int): Affine2D = when (((degrees % 360) + 360) % 360) {
            0 -> IDENTITY
            // (x, y) -> (1 - y, x)
            90 -> Affine2D(a = 0f, b = 1f, c = -1f, d = 0f, tx = 1f, ty = 0f)
            // (x, y) -> (1 - x, 1 - y)
            180 -> Affine2D(a = -1f, b = 0f, c = 0f, d = -1f, tx = 1f, ty = 1f)
            // (x, y) -> (y, 1 - x)
            270 -> Affine2D(a = 0f, b = -1f, c = 1f, d = 0f, tx = 0f, ty = 1f)
            else -> throw IllegalArgumentException("Rotation must be a multiple of 90: $degrees")
        }

        /**
         * Counter-clockwise rotation by [degrees] (a multiple of 90) about (0.5, 0.5), in
         * GL texture coordinates (y up). Matches android.opengl.Matrix.rotateM about z.
         */
        fun rotateCcwGl(degrees: Int): Affine2D {
            val (cos, sin) = when (((degrees % 360) + 360) % 360) {
                0 -> 1f to 0f
                90 -> 0f to 1f
                180 -> -1f to 0f
                270 -> 0f to -1f
                else -> throw IllegalArgumentException("Rotation must be a multiple of 90: $degrees")
            }
            // p' = c + R (p - c), with c = (0.5, 0.5)
            return Affine2D(
                a = cos, b = sin, c = -sin, d = cos,
                tx = 0.5f - 0.5f * cos + 0.5f * sin,
                ty = 0.5f - 0.5f * sin - 0.5f * cos,
            )
        }

        /**
         * Center-crop ("aspect fill"): maps output coordinates into the part of the image
         * that is visible when an image with [imageAspect] (w/h) fills an output with
         * [outputAspect] without letterboxing.
         */
        fun centerCrop(outputAspect: Float, imageAspect: Float): Affine2D =
            if (outputAspect > imageAspect) {
                val s = imageAspect / outputAspect // fraction of image height visible
                Affine2D(d = s, ty = 0.5f - 0.5f * s)
            } else {
                val s = outputAspect / imageAspect // fraction of image width visible
                Affine2D(a = s, tx = 0.5f - 0.5f * s)
            }
    }
}
