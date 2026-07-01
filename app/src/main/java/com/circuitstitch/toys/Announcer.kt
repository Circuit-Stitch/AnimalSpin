package com.circuitstitch.toys

import android.content.Context
import android.content.res.Configuration
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.circuitstitch.toys.models.AnimalNoise
import timber.log.Timber
import java.util.Locale

/**
 * Speaks an animal's name via TextToSpeech, then plays a recorded clip. The seam that keeps
 * [ui.main.MainViewModel] free of the Android speech/media engines so it stays unit-testable
 * (see RealAnnouncer for the real thing; tests supply a fake).
 */
interface Announcer {
    /** Speak the animal's name (when TTS is enabled), then play [noise]'s recorded clip. */
    fun announce(noise: AnimalNoise)

    /** Release TTS + MediaPlayer. Call from the owner's onCleared. */
    fun release()
}

/**
 * The real adapter: owns TextToSpeech + MediaPlayer. Settings are re-read from prefs on every
 * [announce] call, so changes saved in Settings always apply with no lifecycle plumbing (this
 * replaces the old MainFragment.onResume workaround). On async TTS init it resolves the spoken
 * language once and persists the available voices to prefs for SettingsScreen to render.
 */
class RealAnnouncer(
    private val context: Context = AnimalSpinApp.applicationContext(),
    private val prefs: SharedPreferencesProvider = SharedPreferencesProvider(),
) : Announcer {

    private var mp: MediaPlayer? = null
    private var ttsReady = false

    // The language TTS actually speaks. Resolved once at init to the device locale (when we
    // ship a translation for it and the device has voice data), else English. The spoken
    // phrase is always read from a Context set to THIS locale (see [announce]), so the voice's
    // language and the text's language never disagree.
    private var ttsLocale: Locale = Locale.US

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsLocale = resolveTtsLocale()
            ttsReady = true
            val options = availableVoiceOptions()
            Timber.d("tts locale %s, available voices (%d): %s", ttsLocale, options.size, options)
            prefs.setVoiceOptions(options)
        }
    }

    override fun announce(noise: AnimalNoise) {
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
        val says = localizedContext().getString(noise.animal.tts_says)
        tts.speak(says, TextToSpeech.QUEUE_FLUSH, null, says)
    }

    override fun release() {
        tts.shutdown()
        mp?.release()
        mp = null
    }

    private fun playClip(noise: AnimalNoise) {
        mp = MediaPlayer.create(context, noise.noiseFile)
        mp?.start()
    }

    private fun applyVoiceSettings() {
        tts.setSpeechRate(prefs.getVoiceSpeed())
        tts.setPitch(prefs.getVoicePitch())
        // Only honor a saved voice from the language we're speaking — otherwise a leftover
        // (or default) English voice would silently switch the engine back to English.
        prefs.getSelectedVoiceName()?.let { name ->
            tts.voices.firstOrNull { it.name == name && it.locale.language == ttsLocale.language }
                ?.let { tts.voice = it }
        }
    }

    // Prefer the device language, but only when we ship a translation for it (so spoken text
    // exists) AND the device has voice data for it. Otherwise fall back to English — the
    // recorded animal clip plays regardless, so playback never fully fails.
    private fun resolveTtsLocale(): Locale {
        val device = Locale.getDefault()
        if (device.language in SUPPORTED_LANGUAGES &&
            tts.setLanguage(device) >= TextToSpeech.LANG_AVAILABLE
        ) return device
        Timber.d("no shipped TTS for %s; speaking English", device)
        tts.setLanguage(Locale.US)
        return Locale.US
    }

    // Reads string resources in [ttsLocale] instead of the device locale, guaranteeing the
    // spoken phrase is in the same language the voice speaks (never English text in a foreign
    // accent, or vice-versa).
    private fun localizedContext() =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(ttsLocale) }
        )

    // Offline voices for the spoken language (any region, any quality). Serialized
    // "id|region|quality" so SettingsScreen can show friendly grouped names (region + quality
    // come from the live Voice and aren't recoverable from the id alone).
    private fun availableVoiceOptions(): List<String> =
        tts.voices.orEmpty()
            .filter { it.locale.language == ttsLocale.language && !it.isNetworkConnectionRequired }
            .map { "${it.name}|${it.locale.displayName}|${qualityLabel(it.quality)}" }

    private fun qualityLabel(quality: Int): String = when {
        quality >= Voice.QUALITY_HIGH -> "High"
        quality >= Voice.QUALITY_NORMAL -> "Normal"
        else -> "Low"
    }

    companion object {
        // Languages we ship translations for (mirror the values-<code>/ dirs). Add the code
        // here when you add a translation, or that locale will keep speaking English.
        private val SUPPORTED_LANGUAGES = setOf("en", "es", "fr", "de", "pt", "it", "hi", "id", "ja", "ru", "ko", "tr", "vi", "th", "pl", "nl", "ar")
    }
}
