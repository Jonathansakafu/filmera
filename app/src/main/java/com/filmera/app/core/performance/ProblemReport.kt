package com.filmera.app.core.performance

import android.content.Context
import android.os.Process
import com.filmera.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds a plain-text report of this device plus FILMERA's own recent log lines, so a
 * user can send it when something goes wrong. Apps can only read their own log lines,
 * so nothing from other apps is included. No photos, videos or camera frames.
 */
object ProblemReport {

    suspend fun build(context: Context, note: String? = null): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("FILMERA problem report · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            if (note != null) appendLine("Note: $note")
            appendLine()
            append(DeviceInfo.collect(context).asText())
            appendLine()
            appendLine("---- Recent app log ----")
            append(recentLog())
        }
    }

    private fun recentLog(): String = try {
        val proc = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "-t", "600", "--pid=${Process.myPid()}"),
        )
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        // Keep the lines that matter: our tags, camera stack, warnings and errors.
        text.lineSequence()
            .filter { line ->
                line.contains("CameraGlPipeline") || line.contains("FilterRenderer") ||
                    line.contains("CameraViewModel") || line.contains("FaceTracker") ||
                    line.contains("Camera") || line.contains(" E/") || line.contains(" W/")
            }
            .toList()
            .takeLast(250)
            .joinToString("\n")
            .ifEmpty { "(no log lines)" }
    } catch (e: Exception) {
        "(log unavailable: ${e.message})"
    }
}
