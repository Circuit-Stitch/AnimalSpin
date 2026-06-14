package casa.falconer.toys.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import casa.falconer.toys.SharedPreferencesProvider
import timber.log.Timber

class SettingsViewModel : ViewModel() {
    private val prefs = SharedPreferencesProvider()

    val voiceOptions: List<String> = prefs.getVoiceOptions()
    var selectedVoiceName by mutableStateOf(prefs.getSelectedVoiceName() ?: voiceOptions.firstOrNull())
    var voicePitch by mutableStateOf(prefs.getVoicePitch().coerceIn(VOICE_MIN, VOICE_MAX))
    var voiceSpeed by mutableStateOf(prefs.getVoiceSpeed().coerceIn(VOICE_MIN, VOICE_MAX))

    fun save() {
        Timber.d("saving options")
        prefs.setVoicePitch(voicePitch)
        prefs.setVoiceSpeed(voiceSpeed)
        selectedVoiceName?.let { prefs.setSelectedVoiceName(it) }
    }

    companion object {
        // Must match the Slider valueRange in SettingsScreen.
        const val VOICE_MIN = 0.5f
        const val VOICE_MAX = 2.0f
    }
}
