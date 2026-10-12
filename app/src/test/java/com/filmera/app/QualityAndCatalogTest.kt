package com.filmera.app

import com.filmera.app.core.camera.CameraLevel
import com.filmera.app.core.camera.QualityProfile
import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterShaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAndCatalogTest {

    @Test
    fun lowRamPhonesGetPerformance() {
        assertEquals(QualityProfile.PERFORMANCE, QualityProfile.recommend(2_048, 8, CameraLevel.FULL))
        assertEquals(QualityProfile.PERFORMANCE, QualityProfile.recommend(8_000, 8, CameraLevel.LEGACY))
    }

    @Test
    fun flagshipsGetUltra() {
        assertEquals(QualityProfile.ULTRA, QualityProfile.recommend(8_000, 8, CameraLevel.LEVEL_3))
    }

    @Test
    fun midRangeGetsBalanced() {
        assertEquals(QualityProfile.BALANCED, QualityProfile.recommend(4_000, 8, CameraLevel.FULL))
        assertEquals(QualityProfile.BALANCED, QualityProfile.recommend(8_000, 8, CameraLevel.LIMITED))
    }

    @Test
    fun filterIdsAreUnique() {
        val ids = FilterCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun unknownIdFallsBackToOriginal() {
        assertEquals(FilterCatalog.ORIGINAL, FilterCatalog.find("nope"))
        assertEquals(FilterCatalog.ORIGINAL, FilterCatalog.find(null))
    }

    @Test
    fun generatedShadersAreWellFormed() {
        for (f in FilterCatalog.all) {
            val src = FilterShaders.fragment(f)
            assertEquals("braces in ${f.id}", src.count { it == '{' }, src.count { it == '}' })
            assertEquals("parens in ${f.id}", src.count { it == '(' }, src.count { it == ')' })
            assertTrue("${f.id} must return", "return" in f.shader)
            // GLSL pow() is undefined for negative bases; filters must not rely on it.
            assertFalse("${f.id} uses pow on a possibly negative base", Regex("""pow\(\s*x \* x""").containsMatchIn(f.shader))
        }
    }

    @Test
    fun faceFiltersHandleMissingFace() {
        FilterCatalog.all.filter { it.requiresFaceTracking }.forEach {
            assertTrue("${it.id} should check uFace", "uFace" in it.shader)
        }
    }
}
