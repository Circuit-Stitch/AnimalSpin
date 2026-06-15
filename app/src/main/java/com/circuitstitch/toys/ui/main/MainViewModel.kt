package com.circuitstitch.toys.ui.main

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import com.circuitstitch.toys.AnimalSpinApp
import com.circuitstitch.toys.SharedPreferencesProvider
import com.circuitstitch.toys.models.Animal
import com.circuitstitch.toys.models.AnimalNoise
import com.circuitstitch.toys.models.AnimalNoise.Companion.animal_sounds
import timber.log.Timber
import java.util.Locale

/**
 * Owns playback: TextToSpeech speaks the animal's name, then a random recorded clip plays.
 * Settings are re-read from prefs on every [play] call, so changes saved in Settings always
 * apply with no lifecycle plumbing (this replaces the old MainFragment.onResume workaround).
 */
class MainViewModel : ViewModel() {

    private val context = AnimalSpinApp.applicationContext()
    private val prefs = SharedPreferencesProvider()
    private val noisesByAnimal: Map<Animal, List<AnimalNoise>> = animal_sounds.groupBy { it.animal }

    private var mp: MediaPlayer? = null
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(TTS_LANGUAGE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.e("This Language is not supported")
            } else {
                ttsReady = true
                val options = availableVoiceOptions()
                Timber.d("available voices (%d): %s", options.size, options)
                prefs.setVoiceOptions(options)
            }
        }
    }

    fun play(animal: Animal) {
        val noise = noisesByAnimal[animal]?.randomOrNull() ?: return
        mp?.stop()

        // TTS intro disabled by the parent → skip straight to the clip.
        if (!prefs.getTtsEnabled()) {
            playClip(noise)
            return
        }

        if (!ttsReady) return
        applyVoiceSettings()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Timber.d("tts started")
            override fun onDone(utteranceId: String?) {
                Timber.d("tts finished")
                playClip(noise)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = Timber.d("tts error")
        })
        val says = context.getString(animal.tts_says)
        tts.speak(says, TextToSpeech.QUEUE_FLUSH, null, says)
    }

    private fun playClip(noise: AnimalNoise) {
        mp = MediaPlayer.create(context, noise.noiseFile)
        mp?.start()
    }

    private fun applyVoiceSettings() {
        tts.setSpeechRate(prefs.getVoiceSpeed())
        tts.setPitch(prefs.getVoicePitch())
        prefs.getSelectedVoiceName()?.let { name ->
            tts.voices.firstOrNull { it.name == name }?.let { tts.voice = it }
        }
    }

    // All offline English voices (any region, any quality) — broadens the old US-only,
    // high-quality pool so lower-fidelity/robotic voices can be picked too. Each option is
    // serialized "id|region|quality" so SettingsScreen can show friendly grouped names
    // (region + quality come from the live Voice and aren't recoverable from the id alone).
    private fun availableVoiceOptions(): List<String> =
        tts.voices.orEmpty()
            .filter { it.locale.language == TTS_LANGUAGE.language && !it.isNetworkConnectionRequired }
            .map { "${it.name}|${it.locale.displayName}|${qualityLabel(it.quality)}" }

    private fun qualityLabel(quality: Int): String = when {
        quality >= Voice.QUALITY_HIGH -> "High"
        quality >= Voice.QUALITY_NORMAL -> "Normal"
        else -> "Low"
    }

    override fun onCleared() {
        tts.shutdown()
        mp?.release()
        mp = null
    }

    companion object {
        private val TTS_LANGUAGE: Locale = Locale.US
    }
}
