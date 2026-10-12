package com.filmera.app

import com.filmera.app.core.filter.FilterCatalog
import com.filmera.app.core.filter.FilterShaders
import org.junit.Test
import java.io.File

/**
 * Writes every generated shader to build/shaders so they can be checked offline with
 * Khronos glslangValidator (see README, "Validating shaders").
 */
class ShaderDumpTest {
    @Test
    fun dumpShaders() {
        val dir = File("build/shaders").apply { mkdirs() }
        File(dir, "vertex.vert").writeText("#version 100\n" + FilterShaders.VERTEX)
        FilterCatalog.all.forEach { f ->
            File(dir, "${f.id}.frag").writeText("#version 100\n" + FilterShaders.fragment(f))
        }
    }
}
