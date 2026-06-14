package casa.falconer.toys.ui.main

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import casa.falconer.toys.AnimalSpinApp
import casa.falconer.toys.SharedPreferencesProvider
import casa.falconer.toys.models.Animal
import casa.falconer.toys.models.AnimalNoise
import casa.falconer.toys.models.AnimalNoise.Companion.animal_sounds
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
                prefs.setVoiceOptions(availableVoiceNames())
            }
        }
    }

    fun play(animal: Animal) {
        if (!ttsReady) return
        val noise = noisesByAnimal[animal]?.randomOrNull() ?: return

        mp?.stop()
        applyVoiceSettings()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Timber.d("tts started")
            override fun onDone(utteranceId: String?) {
                Timber.d("tts finished")
                mp = MediaPlayer.create(context, noise.noiseFile)
                mp?.start()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = Timber.d("tts error")
        })
        val says = context.getString(animal.tts_says)
        tts.speak(says, TextToSpeech.QUEUE_FLUSH, null, says)
    }

    private fun applyVoiceSettings() {
        tts.setSpeechRate(prefs.getVoiceSpeed())
        tts.setPitch(prefs.getVoicePitch())
        prefs.getSelectedVoiceName()?.let { name ->
            tts.voices.firstOrNull { it.name == name }?.let { tts.voice = it }
        }
    }

    private fun availableVoiceNames(): List<String> =
        tts.voices
            .filter { it.locale == TTS_LANGUAGE && !it.isNetworkConnectionRequired }
            .map { it.name }

    override fun onCleared() {
        tts.shutdown()
        mp?.release()
        mp = null
    }

    companion object {
        private val TTS_LANGUAGE: Locale = Locale.US
    }
}
