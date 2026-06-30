package com.circuitstitch.toys.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.circuitstitch.toys.R
import com.circuitstitch.toys.ui.main.SettingsViewModel.Companion.VOICE_MAX
import com.circuitstitch.toys.ui.main.SettingsViewModel.Companion.VOICE_MIN

@Composable
fun SettingsScreen(onDone: () -> Unit, vm: SettingsViewModel = viewModel()) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp) // ponytail: cap width before fillMaxWidth so tablets don't spread edge-to-edge
            .fillMaxWidth()
            .align(Alignment.TopCenter)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.speak_animal_name).uppercase())
            Switch(checked = vm.ttsEnabled, onCheckedChange = { vm.ttsEnabled = it })
        }

        // Voice tuning only matters when the intro is spoken.
        if (vm.ttsEnabled) {
            VoiceDropdown(
                options = vm.voiceOptions,
                selectedId = vm.selectedVoiceId,
                onSelect = { vm.selectedVoiceId = it },
            )

            Text(text = stringResource(R.string.presets).uppercase())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            SettingsViewModel.PRESETS.forEach { preset ->
                // wrap-content + trimmed padding/font (see dimens.xml) so all three pills
                // fit one row without clipping. toSp() round-trips the sp dimen, keeping font scaling.
                Button(
                    onClick = { vm.applyPreset(preset) },
                    contentPadding = PaddingValues(
                        horizontal = dimensionResource(R.dimen.preset_button_padding_horizontal),
                        vertical = dimensionResource(R.dimen.preset_button_padding_vertical),
                    ),
                ) {
                    Text(
                        text = stringResource(preset.label).uppercase(),
                        maxLines = 1,
                        fontSize = with(LocalDensity.current) {
                            dimensionResource(R.dimen.preset_button_text_size).toSp()
                        },
                    )
                }
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
        }

        Button(
            onClick = { vm.save(); onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_btn).uppercase())
        }

        val creditsSize = with(LocalDensity.current) { dimensionResource(R.dimen.credits_text_size).toSp() }
        val creditsLineHeight = with(LocalDensity.current) { dimensionResource(R.dimen.credits_line_height).toSp() }
        val logoSize = dimensionResource(R.dimen.credits_logo_size)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_circuit_stitch),
                contentDescription = null,
                modifier = Modifier.size(logoSize),
            )
            Text(
                text = "Animal Spin\nby\nKyle Falconer\nCircuit Stitch\n2026",
                fontSize = creditsSize,
                lineHeight = creditsLineHeight,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = painterResource(R.drawable.logo_github),
                contentDescription = null,
                modifier = Modifier.size(logoSize),
            )
        }

        Text(
            text = "all sounds and images are public domain or CC BY-SA\n" +
                "project source code is at\ngithub.com/Circuit-Stitch/AnimalSpin",
            fontSize = creditsSize,
            lineHeight = creditsLineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
    }
}

// Quality tag only when worth flagging — most devices report every neural voice as "High".
private fun SettingsViewModel.VoiceOption.label(): String =
    if (quality == "High") name else "$name  ·  $quality"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
    options: List<SettingsViewModel.VoiceOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.region} — ${it.label()}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.voices).uppercase()) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.groupBy { it.region }.forEach { (region, voices) ->
                Text(
                    text = region,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.label()) },
                        onClick = { onSelect(voice.id); expanded = false },
                    )
                }
            }
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
