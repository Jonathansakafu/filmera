package com.filmera.app.core.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.filmera.app.core.face.FaceData
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterDefinition
import com.filmera.app.core.filter.FilterShaders
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Per-frame values that are the same for every output surface. */
data class FrameParams(
    val filter: FilterDefinition,
    val intensity: Float,
    /** brightness, contrast, saturation, warmth; each -1..1. */
    val adjust: FloatArray,
    val timeSeconds: Float,
    val face: FaceData?,
)

/**
 * Draws the camera's OES texture through a filter's shader. Programs are compiled lazily
 * and cached per filter. Must be used on the GL thread.
 */
class FilterRenderer {

    private class Program(
        val id: Int,
        val aPosition: Int,
        val aOutputUv: Int,
        val uniforms: Map<String, Int>,
    ) {
        fun u(name: String) = uniforms[name] ?: -1
    }

    private val programs = HashMap<String, Program?>()
    private val failed = HashSet<String>()

    /** Ids of filters whose shaders failed to compile on this GPU. */
    val failedFilters: Set<String> get() = failed

    private val positions = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    // Output uv uses the image convention (top-left origin), so the GL bottom-left
    // vertex gets v = 1.
    private val outputUvs = floatBuffer(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

    private val imageToTex = FloatArray(16)

    /**
     * Draws [texture] through the current filter. [external] is true for the live camera
     * (OES texture) and false for a full-resolution photo in a 2D texture.
     * [texelScale] < 1 makes blur/sharpen radii match the preview when the source is
     * larger than the preview, so a photo looks like what was on screen.
     */
    fun draw(
        texture: Int,
        surfaceTextureMatrix: FloatArray,
        geometry: FrameGeometry,
        params: FrameParams,
        external: Boolean = true,
        texelScale: Float = 1f,
    ) {
        val program = programFor(params.filter, external)
            ?: programFor(FilterCatalog.ORIGINAL, external)
            ?: (if (external) passthrough() else null)
            ?: return
        GLES20.glViewport(0, 0, geometry.outputWidth, geometry.outputHeight)
        GLES20.glUseProgram(program.id)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(program.u("uTex"), 0)

        Matrix.multiplyMM(imageToTex, 0, surfaceTextureMatrix, 0, geometry.imageToGlTexture().toMat4(), 0)
        GLES20.glUniformMatrix4fv(program.u("uImageToTex"), 1, false, imageToTex, 0)
        GLES20.glUniformMatrix4fv(program.u("uOutputToImage"), 1, false, geometry.outputToImage().toMat4(), 0)

        GLES20.glUniform2f(
            program.u("uTexel"),
            1f / (geometry.imageWidth * texelScale),
            1f / (geometry.imageHeight * texelScale),
        )
        GLES20.glUniform1f(program.u("uAspect"), geometry.imageAspect)
        GLES20.glUniform1f(program.u("uTime"), params.timeSeconds)
        GLES20.glUniform1f(program.u("uIntensity"), params.intensity)
        val a = params.adjust
        GLES20.glUniform4f(program.u("uAdjust"), a[0], a[1], a[2], a[3])

        val face = params.face
        GLES20.glUniform1f(program.u("uFace"), if (face != null) 1f else 0f)
        if (face != null) {
            setPoint(program, "uEyeA", face.eyeA)
            setPoint(program, "uEyeB", face.eyeB)
            setPoint(program, "uNose", face.nose)
            setPoint(program, "uMouth", face.mouth)
            setPoint(program, "uCheekA", face.cheekA)
            setPoint(program, "uCheekB", face.cheekB)
            setPoint(program, "uForehead", face.forehead)
            setPoint(program, "uChin", face.chin)
        }

        GLES20.glEnableVertexAttribArray(program.aPosition)
        GLES20.glVertexAttribPointer(program.aPosition, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(program.aOutputUv)
        GLES20.glVertexAttribPointer(program.aOutputUv, 2, GLES20.GL_FLOAT, false, 0, outputUvs)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(program.aPosition)
        GLES20.glDisableVertexAttribArray(program.aOutputUv)
    }

    private fun setPoint(program: Program, name: String, p: FloatArray) {
        GLES20.glUniform2f(program.u(name), p[0], p[1])
    }

    private var passthroughProgram: Program? = null
    private var passthroughFailed = false

    /**
     * Last resort when even Original fails on a GPU: a minimal shader with none of the
     * shared helpers, so the user still sees their camera.
     */
    private fun passthrough(): Program? {
        if (passthroughFailed) return null
        passthroughProgram?.let { return it }
        return try {
            build(PASSTHROUGH).also { passthroughProgram = it }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Passthrough shader failed", e)
            passthroughFailed = true
            null
        }
    }

    private fun programFor(filter: FilterDefinition, external: Boolean = true): Program? {
        val key = if (external) filter.id else "${filter.id}#2d"
        if (key in failed) return null
        return programs.getOrPut(key) {
            try {
                build(FilterShaders.fragment(filter, external))
            } catch (e: RuntimeException) {
                Log.e(TAG, "Filter '$key' failed to compile", e)
                failed += key
                null
            }
        }
    }

    private fun build(fragmentSource: String): Program {
        val id = GlUtil.createProgram(FilterShaders.VERTEX, fragmentSource)
        val names = listOf(
            "uTex", "uImageToTex", "uOutputToImage", "uTexel", "uAspect", "uTime", "uIntensity",
            "uAdjust", "uFace", "uEyeA", "uEyeB", "uNose", "uMouth", "uCheekA", "uCheekB",
            "uForehead", "uChin",
        )
        return Program(
            id = id,
            aPosition = GLES20.glGetAttribLocation(id, "aPosition"),
            aOutputUv = GLES20.glGetAttribLocation(id, "aOutputUv"),
            uniforms = names.associateWith { GLES20.glGetUniformLocation(id, it) },
        )
    }

    fun release() {
        programs.values.filterNotNull().forEach { GLES20.glDeleteProgram(it.id) }
        programs.clear()
    }

    private companion object {
        const val TAG = "FilterRenderer"

        val PASSTHROUGH = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vImageUv;
uniform samplerExternalOES uTex;
uniform mat4 uImageToTex;
void main() {
    gl_FragColor = vec4(texture2D(uTex, (uImageToTex * vec4(vImageUv, 0.0, 1.0)).xy).rgb, 1.0);
}
"""

        fun floatBuffer(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(values); position(0) }
    }
}
