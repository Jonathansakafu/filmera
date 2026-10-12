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
    fun imageToGlTexture(): Affine2D = imageToBuffer().then(Affine2D.flipY())
}
