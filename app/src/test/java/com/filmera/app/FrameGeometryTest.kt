package com.filmera.app

import com.filmera.app.core.render.Affine2D
import com.filmera.app.core.render.FrameGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameGeometryTest {

    private fun assertPoint(m: Affine2D, u: Float, v: Float, ex: Float, ey: Float) {
        assertEquals("x for ($u,$v)", ex, m.mapX(u, v), 1e-5f)
        assertEquals("y for ($u,$v)", ey, m.mapY(u, v), 1e-5f)
    }

    @Test
    fun rotate90MovesTopLeftToTopRight() {
        assertPoint(Affine2D.rotateClockwise(90), 0f, 0f, 1f, 0f)
        assertPoint(Affine2D.rotateClockwise(90), 1f, 0f, 1f, 1f)
    }

    @Test
    fun rotationsComposeToIdentity() {
        val m = Affine2D.rotateClockwise(90).then(Affine2D.rotateClockwise(270))
        assertPoint(m, 0.2f, 0.7f, 0.2f, 0.7f)
        val n = Affine2D.rotateClockwise(180).then(Affine2D.rotateClockwise(180))
        assertPoint(n, 0.3f, 0.1f, 0.3f, 0.1f)
    }

    @Test
    fun imageToBufferUndoesBufferRotation() {
        val g = FrameGeometry(1280, 720, 90, false, 720, 1280)
        // Buffer point -> upright via rotation, then back through imageToBuffer.
        val toUpright = Affine2D.rotateClockwise(90)
        val roundTrip = toUpright.then(g.imageToBuffer())
        assertPoint(roundTrip, 0.25f, 0.6f, 0.25f, 0.6f)
    }

    @Test
    fun portraitScreenShowsWholeUprightImage() {
        // 16:9 sensor buffer rotated to 9:16, shown on a 9:16 screen: no crop.
        val g = FrameGeometry(1280, 720, 90, false, 1080, 1920)
        assertEquals(720, g.imageWidth)
        assertPoint(g.outputToImage(), 0f, 0f, 0f, 0f)
        assertPoint(g.outputToImage(), 1f, 1f, 1f, 1f)
    }

    @Test
    fun tallerScreenCropsSides() {
        // 9:20 screen showing a 9:16 image: width is cropped, height is full.
        val g = FrameGeometry(1280, 720, 90, false, 900, 2000)
        val m = g.outputToImage()
        assertEquals(0f, m.mapY(0.5f, 0f), 1e-5f)
        assertEquals(1f, m.mapY(0.5f, 1f), 1e-5f)
        assertEquals(0.5f, m.mapX(0.5f, 0.5f), 1e-5f)
        val visible = m.mapX(1f, 0f) - m.mapX(0f, 0f)
        assertEquals((900f / 2000f) / (720f / 1280f), visible, 1e-4f)
    }

    @Test
    fun mirrorFlipsHorizontally() {
        val g = FrameGeometry(1280, 720, 270, true, 720, 1280)
        assertPoint(g.outputToImage(), 0f, 0.5f, 1f, 0.5f)
    }

    @Test
    fun landscapeVideoWithRotationHintMapsToUprightImage() {
        // Encoder surface is 1280x720 and the file says "rotate 90 on playback".
        val g = FrameGeometry(1280, 720, 90, false, 1280, 720, outputRotation = 90)
        val m = g.outputToImage()
        // Stored top-left ends up top-right after playback rotation.
        assertPoint(m, 0f, 0f, 1f, 0f)
        // Stored bottom-left ends up top-left.
        assertPoint(m, 0f, 1f, 0f, 0f)
    }

    @Test
    fun bottomUpOutputIsFlipped() {
        val g = FrameGeometry(1280, 720, 90, false, 720, 1280, outputBottomUp = true)
        assertPoint(g.outputToImage(), 0f, 0f, 0f, 1f)
    }

    @Test
    fun rotateCcwGlMatchesRotateM() {
        // 90° CCW about the center in y-up coords: right-middle goes to top-middle.
        assertPoint(Affine2D.rotateCcwGl(90), 1f, 0.5f, 0.5f, 1f)
        assertPoint(Affine2D.rotateCcwGl(270), 1f, 0.5f, 0.5f, 0f)
    }

    @Test
    fun inverseUndoesMap() {
        val m = Affine2D.rotateCcwGl(90).then(Affine2D.centerCrop(0.5f, 0.7f)).then(Affine2D.flipX())
        assertPoint(m.then(m.inverse()), 0.3f, 0.8f, 0.3f, 0.8f)
    }

    private fun stOf(m: Affine2D) = m.toMat4()

    /**
     * Drivers that pre-transform the buffer report st' = st · R · M. Reading the sign pattern
     * of st' must recover a correction that makes (correction, then st') equal st, for every
     * quarter-turn and mirror, with a realistic crop scale in st.
     */
    @Test
    fun driverTransformIsUndoneFromMatrix() {
        val stPlain = Affine2D.flipY().then(Affine2D(a = 0.98f, d = 0.97f, tx = 0.01f, ty = 0.015f))
        for (rot in listOf(0, 90, 180, 270)) for (mirror in listOf(false, true)) {
            val rm = (if (mirror) Affine2D.flipX() else Affine2D.IDENTITY).then(Affine2D.rotateCcwGl(rot))
            val stDriver = rm.then(stPlain) // point map of st · R · M
            val corrected = FrameGeometry.orientationCorrection(stOf(stDriver)).then(stDriver)
            for ((u, v) in listOf(0f to 0f, 1f to 0f, 0.25f to 0.75f)) {
                assertEquals("rot=$rot mirror=$mirror", stPlain.mapX(u, v), corrected.mapX(u, v), 1e-5f)
                assertEquals("rot=$rot mirror=$mirror", stPlain.mapY(u, v), corrected.mapY(u, v), 1e-5f)
            }
        }
    }

    @Test
    fun plainMatrixNeedsNoCorrection() {
        val stPlain = Affine2D.flipY().then(Affine2D(a = 0.98f, d = 0.97f, tx = 0.01f, ty = 0.015f))
        assertEquals(Affine2D.IDENTITY, FrameGeometry.orientationCorrection(stOf(stPlain)))
    }
}
