package casa.falconer.toys.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import casa.falconer.toys.R
import com.google.android.material.slider.Slider
import timber.log.Timber

class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val voicesRadioGroup = view.findViewById<RadioGroup>(R.id.voiceSelectionRadios)
        voicesRadioGroup.orientation = RadioGroup.VERTICAL
        for (voicename in viewModel.voiceOptions) {
            val rb = RadioButton(context)
            rb.text = voicename
            rb.id = voicename.hashCode()
            voicesRadioGroup.addView(rb)
        }
        viewModel.getSelectedVoiceId()?.let {
            voicesRadioGroup.check(it)
        }
        voicesRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            viewModel.setSelectedVoiceId(checkedId)
            Timber.d("voice changed to ${viewModel.selectedVoiceName}")
        }

        fun pct(value: Float) = "${(value * 100).toInt()}%"

        val pitchValue = view.findViewById<TextView>(R.id.voicePitchValue)
        val pitchSlicer = view.findViewById<Slider>(R.id.voicePitchSlider)
        pitchSlicer.value = viewModel.voicePitch.coerceIn(VOICE_MIN, VOICE_MAX)
        pitchValue.text = pct(pitchSlicer.value)
        pitchSlicer.addOnChangeListener { _, value, _ ->
            viewModel.voicePitch = value
            pitchValue.text = pct(value)
        }

        val speedValue = view.findViewById<TextView>(R.id.voiceSpeedValue)
        val speedSlicer = view.findViewById<Slider>(R.id.voiceSpeedSlider)
        speedSlicer.value = viewModel.voiceSpeed.coerceIn(VOICE_MIN, VOICE_MAX)
        speedValue.text = pct(speedSlicer.value)
        speedSlicer.addOnChangeListener { _, value, _ ->
            viewModel.voiceSpeed = value
            speedValue.text = pct(value)
        }

        val saveButton: AppCompatButton = view.findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            viewModel.saveOptions()
            findNavController().navigateUp()
        }

        return view
    }

    companion object {
        // Must match valueFrom/valueTo on the sliders in fragment_settings.xml.
        private const val VOICE_MIN = 0.5f
        private const val VOICE_MAX = 2.0f
    }

}