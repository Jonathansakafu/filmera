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

    fun draw(
        oesTexture: Int,
        surfaceTextureMatrix: FloatArray,
        geometry: FrameGeometry,
        params: FrameParams,
    ) {
        val program = programFor(params.filter) ?: programFor(FilterCatalog.ORIGINAL) ?: return
        GLES20.glViewport(0, 0, geometry.outputWidth, geometry.outputHeight)
        GLES20.glUseProgram(program.id)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(program.u("uTex"), 0)

        Matrix.multiplyMM(imageToTex, 0, surfaceTextureMatrix, 0, geometry.imageToGlTexture().toMat4(), 0)
        GLES20.glUniformMatrix4fv(program.u("uImageToTex"), 1, false, imageToTex, 0)
        GLES20.glUniformMatrix4fv(program.u("uOutputToImage"), 1, false, geometry.outputToImage().toMat4(), 0)

        GLES20.glUniform2f(program.u("uTexel"), 1f / geometry.imageWidth, 1f / geometry.imageHeight)
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

    private fun programFor(filter: FilterDefinition): Program? {
        if (filter.id in failed) return null
        return programs.getOrPut(filter.id) {
            try {
                build(FilterShaders.fragment(filter))
            } catch (e: RuntimeException) {
                Log.e(TAG, "Filter '${filter.id}' failed to compile", e)
                failed += filter.id
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

        fun floatBuffer(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(values); position(0) }
    }
}
