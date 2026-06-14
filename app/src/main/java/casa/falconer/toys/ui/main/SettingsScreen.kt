package casa.falconer.toys.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import casa.falconer.toys.R
import casa.falconer.toys.ui.main.SettingsViewModel.Companion.VOICE_MAX
import casa.falconer.toys.ui.main.SettingsViewModel.Companion.VOICE_MIN

@Composable
fun SettingsScreen(onDone: () -> Unit, vm: SettingsViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings).uppercase(),
            fontSize = 32.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Text(text = stringResource(R.string.voices).uppercase())
        vm.voiceOptions.forEach { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = vm.selectedVoiceName == name,
                        onClick = { vm.selectedVoiceName = name },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = vm.selectedVoiceName == name,
                    onClick = { vm.selectedVoiceName = name },
                )
                Text(name)
            }
        }

        SliderRow(
            label = stringResource(R.string.voice_pitch),
            value = vm.voicePitch,
            onChange = { vm.voicePitch = it },
        )
        SliderRow(
            label = stringResource(R.string.voice_speed),
            value = vm.voiceSpeed,
            onChange = { vm.voiceSpeed = it },
        )

        Button(
            onClick = { vm.save(); onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_btn).uppercase())
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Text(text = label.uppercase())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = VOICE_MIN..VOICE_MAX,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).toInt()}%",
            modifier = Modifier
                .width(56.dp)
                .padding(start = 10.dp),
            textAlign = TextAlign.End,
        )
    }
}
