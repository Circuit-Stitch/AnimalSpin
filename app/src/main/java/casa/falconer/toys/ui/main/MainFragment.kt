package casa.falconer.toys.ui.main

import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import casa.falconer.toys.R
import casa.falconer.toys.models.Animal
import timber.log.Timber
import java.util.Locale

class MainFragment : Fragment() {

    companion object {
        var mp: MediaPlayer? = null
        var tts: TextToSpeech? = null
        val ttsLanguage: Locale = Locale.US
    }

    private lateinit var viewModel: MainViewModel
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts!!.setLanguage(ttsLanguage)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Timber.e("This Language is not supported")
                    } else {
                        ttsReady = true
                        applyTtsSettings()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Re-apply settings saved in SettingsFragment; this fragment isn't recreated on return.
        if (ttsReady) applyTtsSettings()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        val animalsRV = view.findViewById<RecyclerView>(R.id.animalsRecyclerView)
        animalsRV.layoutManager = GridLayoutManager(context, 2)

        animalsRV.adapter = AnimalsAdapter(Animal.values().asList()) { clickedAnimal ->
            viewModel.queueAnimalNoise(clickedAnimal)
            playAnimalNoise()
        }

        val settingsButton: AppCompatButton = view.findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        }

        return view
    }

    private fun playAnimalNoise() {
        mp?.stop()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Timber.d("tts started")
            }

            override fun onDone(utteranceId: String?) {
                Timber.d("tts finished")

                viewModel.currentAnimalNoise?.let { an ->
                    mp = MediaPlayer.create(context, an.noiseFile)
                    mp?.start()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Timber.d("tts error")
            }
        })
        viewModel.currentAnimalNoise?.let { an ->
            val ttsSays = getString(an.animal.tts_says)
            tts?.speak(ttsSays, TextToSpeech.QUEUE_FLUSH, null, ttsSays)
        }
    }

    private fun applyTtsSettings() {
        val tts = tts ?: return
        viewModel.reloadVoiceSettings()
        tts.setSpeechRate(viewModel.voiceSpeed)
        tts.setPitch(viewModel.voicePitch)

        val voicesMap = tts.voices.filter { voice ->
            voice.locale == ttsLanguage && !voice.isNetworkConnectionRequired
        }.associateBy { it.name }

        viewModel.saveVoiceOptions(voicesMap.values.map { it.name })

        viewModel.getSelectedVoiceName()?.let { selectedVoiceName ->
            voicesMap[selectedVoiceName]?.let { tts.voice = it }
        }
    }
}