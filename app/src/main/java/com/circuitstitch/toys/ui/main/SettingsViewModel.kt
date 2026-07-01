package com.circuitstitch.toys.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.circuitstitch.toys.R
import com.circuitstitch.toys.SharedPreferencesProvider
import timber.log.Timber

class SettingsViewModel : ViewModel() {
    private val prefs = SharedPreferencesProvider()

    // Friendly options grouped under their region, derived from the "id|region|quality"
    // strings MainViewModel persisted. ponytail: labels are built as plain English strings
    // (no string resources) — this app ships English-only, so there's nothing to localize.
    val voiceOptions: List<VoiceOption> = parseVoiceOptions(prefs.getVoiceOptions())
    var selectedVoiceId by mutableStateOf(
        prefs.getSelectedVoiceName() ?: voiceOptions.firstOrNull()?.id
    )
    var voicePitch by mutableFloatStateOf(prefs.getVoicePitch().coerceIn(VOICE_MIN, VOICE_MAX))
    var voiceSpeed by mutableFloatStateOf(prefs.getVoiceSpeed().coerceIn(VOICE_MIN, VOICE_MAX))
    var ttsEnabled by mutableStateOf(prefs.getTtsEnabled())

    fun save() {
        Timber.d("saving options")
        prefs.setVoicePitch(voicePitch)
        prefs.setVoiceSpeed(voiceSpeed)
        prefs.setTtsEnabled(ttsEnabled)
        selectedVoiceId?.let { prefs.setSelectedVoiceName(it) }
    }

    // Sets the sliders + resets to the default voice; user still hits Save to persist.
    fun applyPreset(preset: VoicePreset) {
        voicePitch = preset.pitch
        voiceSpeed = preset.speed
        selectedVoiceId = SharedPreferencesProvider.DEFAULT_VOICE_NAME
            .takeIf { id -> voiceOptions.any { it.id == id } } ?: voiceOptions.firstOrNull()?.id
    }

    data class VoicePreset(@StringRes val label: Int, val pitch: Float, val speed: Float)

    /** id = TTS voice name (persisted), region = group header, name = friendly leaf label. */
    data class VoiceOption(
        val id: String,
        val region: String,
        val quality: String,
        val name: String
    )

    private fun parseVoiceOptions(raw: List<String>): List<VoiceOption> {
        val gender = Regex("#(male|female)_(\\d+)")
        val voiceCounters = HashMap<String, Int>()
        return raw
            .mapNotNull { it.split("|").takeIf { p -> p.size == 3 } }
            .sortedWith(compareBy({ it[1] }, { it[0] }))   // region, then id — stable grouping
            .map { (id, region, quality) ->
                val match = gender.find(id)
                val name = if (match != null) {
                    "${match.groupValues[1].replaceFirstChar { it.uppercase() }} ${match.groupValues[2]}"
                } else {
                    val n = (voiceCounters[region] ?: 0) + 1
                    voiceCounters[region] = n
                    "Voice $n"
                }
                VoiceOption(id, region, quality, name)
            }
    }

    companion object {
        // Must match the Slider valueRange in SettingsScreen.
        const val VOICE_MIN = 0.5f
        const val VOICE_MAX = 2.0f

        // pitch/speed only (no fragile per-device voice), kept inside VOICE_MIN..VOICE_MAX.
        val PRESETS = listOf(
            VoicePreset(R.string.preset_normal, 1.0f, 1.0f),
            VoicePreset(R.string.preset_robot, 0.7f, 0.9f),
            VoicePreset(R.string.preset_chipmunk, 1.8f, 1.3f),
        )
    }
}
