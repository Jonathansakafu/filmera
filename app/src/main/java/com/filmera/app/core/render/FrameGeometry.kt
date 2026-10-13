package com.filmera.app.core.render

/**
 * Describes how one camera frame should be placed on one output surface and builds the
 * two maps the shaders need:
 *
 *  - [outputToImage]: output space -> image space (vertex shader)
 *  - [imageToBuffer]: image space  -> buffer space (fragment shader, before the
 *    SurfaceTexture transform)
 */
data class FrameGeometry(
    /** Camera buffer size as delivered, in sensor orientation. */
    val bufferWidth: Int,
    val bufferHeight: Int,
    /** Clockwise rotation that makes the buffer upright (CameraX rotationDegrees). */
    val bufferRotation: Int,
    /** Mirror the picture horizontally (front camera selfie view). */
    val mirror: Boolean,
    /** Size of the surface being drawn, in its stored orientation. */
    val outputWidth: Int,
    val outputHeight: Int,
    /**
     * Clockwise rotation a viewer applies to the output when showing it (video files carry
     * this as an orientation hint). 0 for the screen.
     */
    val outputRotation: Int = 0,
    /** True when the output's first row is the bottom (glReadPixels on an FBO). */
    val outputBottomUp: Boolean = false,
    /**
     * Undoes any rotation/mirroring the camera driver folded into the SurfaceTexture matrix.
     * See [orientationCorrection].
     */
    val textureCorrection: Affine2D = Affine2D.IDENTITY,
) {
    private val rotatedQuarter get() = bufferRotation % 180 != 0

    val imageWidth get() = if (rotatedQuarter) bufferHeight else bufferWidth
    val imageHeight get() = if (rotatedQuarter) bufferWidth else bufferHeight
    val imageAspect get() = imageWidth.toFloat() / imageHeight

    private val uprightOutputAspect: Float
        get() = if (outputRotation % 180 != 0) outputHeight.toFloat() / outputWidth
        else outputWidth.toFloat() / outputHeight

    fun outputToImage(): Affine2D {
        var m = if (outputBottomUp) Affine2D.flipY() else Affine2D.IDENTITY
        m = m.then(Affine2D.rotateClockwise(outputRotation))
        m = m.then(Affine2D.centerCrop(uprightOutputAspect, imageAspect))
        if (mirror) m = m.then(Affine2D.flipX())
        return m
    }

    /** Inverse of "rotate buffer clockwise by bufferRotation". */
    fun imageToBuffer(): Affine2D = Affine2D.rotateClockwise(360 - bufferRotation)

    /**
     * Image space -> GL texture coordinates expected by SurfaceTexture.getTransformMatrix,
     * which use a bottom-left origin.
     */
    fun imageToGlTexture(): Affine2D =
        imageToBuffer().then(Affine2D.flipY()).then(textureCorrection)

    companion object {
        /**
         * Some camera drivers rotate (and, for front cameras, mirror) preview buffers and
         * fold that into the SurfaceTexture matrix; others don't, and the CameraX
         * `hasCameraTransform()` flag doesn't reliably say which. So read it from the
         * matrix itself: a plain matrix is a vertical flip plus positive scale/offset.
         * Any extra quarter-turn or mirror E shows up in its sign pattern, and the
         * correction applied before the matrix is E⁻¹ (about the texture center).
         *
         * [st] is the column-major 4x4 from SurfaceTexture.getTransformMatrix.
         */
        fun orientationCorrection(st: FloatArray): Affine2D {
            val a = st[0]; val b = st[1]; val c = st[4]; val d = st[5]
            fun sgn(v: Float) = if (v >= 0f) 1f else -1f
            // Sign pattern of the 2x2 part, i.e. the matrix with its crop scale removed.
            val o = if (kotlin.math.abs(a) + kotlin.math.abs(d) >= kotlin.math.abs(b) + kotlin.math.abs(c)) {
                Affine2D(a = sgn(a), b = 0f, c = 0f, d = sgn(d))
            } else {
                Affine2D(a = 0f, b = sgn(b), c = sgn(c), d = 0f)
            }
            // o = flipY · E  =>  E = flipY · o (flipY is its own inverse).
            val e = o.then(Affine2D(d = -1f))
            if (e.a == 1f && e.d == 1f && e.b == 0f && e.c == 0f) return Affine2D.IDENTITY
            val centered = Affine2D(
                e.a, e.b, e.c, e.d,
                tx = 0.5f - 0.5f * (e.a + e.c),
                ty = 0.5f - 0.5f * (e.b + e.d),
            )
            return centered.inverse()
        }
    }
}
