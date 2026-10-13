package com.filmera.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.filmera.app.core.camera.QualitySetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val onboardingDone: Boolean = false,
    val quality: QualitySetting = QualitySetting.AUTO,
    val mirrorFront: Boolean = true,
    val autoSaveToGallery: Boolean = true,
    val recordAudio: Boolean = true,
    val developerMode: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val recentFilters: List<String> = emptyList(),
    val lastFilter: String = "original",
    /** Extra clockwise rotation (0/90/180/270) for devices that report it wrong. */
    val rotationFix: Int = 0,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val quality = stringPreferencesKey("quality")
        val mirrorFront = booleanPreferencesKey("mirror_front")
        val autoSave = booleanPreferencesKey("auto_save")
        val recordAudio = booleanPreferencesKey("record_audio")
        val developerMode = booleanPreferencesKey("developer_mode")
        val perfOverlay = booleanPreferencesKey("perf_overlay")
        val recentFilters = stringPreferencesKey("recent_filters")
        val lastFilter = stringPreferencesKey("last_filter")
        val rotationFix = androidx.datastore.preferences.core.intPreferencesKey("rotation_fix")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            quality = p[Keys.quality]?.let { runCatching { QualitySetting.valueOf(it) }.getOrNull() }
                ?: QualitySetting.AUTO,
            mirrorFront = p[Keys.mirrorFront] ?: true,
            autoSaveToGallery = p[Keys.autoSave] ?: true,
            recordAudio = p[Keys.recordAudio] ?: true,
            developerMode = p[Keys.developerMode] ?: false,
            showPerformanceOverlay = p[Keys.perfOverlay] ?: false,
            recentFilters = p[Keys.recentFilters]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            lastFilter = p[Keys.lastFilter] ?: "original",
            rotationFix = p[Keys.rotationFix] ?: 0,
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setOnboardingDone() = set(Keys.onboardingDone, true)
    suspend fun setQuality(q: QualitySetting) = set(Keys.quality, q.name)
    suspend fun setMirrorFront(v: Boolean) = set(Keys.mirrorFront, v)
    suspend fun setAutoSave(v: Boolean) = set(Keys.autoSave, v)
    suspend fun setRecordAudio(v: Boolean) = set(Keys.recordAudio, v)
    suspend fun setDeveloperMode(v: Boolean) = set(Keys.developerMode, v)
    suspend fun setPerformanceOverlay(v: Boolean) = set(Keys.perfOverlay, v)
    suspend fun setRotationFix(degrees: Int) = set(Keys.rotationFix, ((degrees % 360) + 360) % 360)

    /** Remembers the filter and moves it to the front of the recent list (max 8). */
    suspend fun useFilter(id: String) {
        context.dataStore.edit { p ->
            p[Keys.lastFilter] = id
            if (id == "original") return@edit
            val recent = p[Keys.recentFilters]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            p[Keys.recentFilters] = (listOf(id) + recent.filter { it != id }).take(8).joinToString(",")
        }
    }
}
