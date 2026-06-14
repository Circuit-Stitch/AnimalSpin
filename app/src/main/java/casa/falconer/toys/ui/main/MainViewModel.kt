package casa.falconer.toys.ui.main

import androidx.lifecycle.ViewModel
import casa.falconer.toys.SharedPreferencesProvider
import casa.falconer.toys.models.Animal
import casa.falconer.toys.models.AnimalNoise
import casa.falconer.toys.models.AnimalNoise.Companion.animal_sounds
import timber.log.Timber


class MainViewModel : ViewModel() {

    var prefs: SharedPreferencesProvider = SharedPreferencesProvider()
    val animalNoiseMap = mutableMapOf<Animal, MutableList<AnimalNoise>>()
    var currentAnimalNoise: AnimalNoise? = null
    var voiceSpeed = prefs.getVoiceSpeed()
    var voicePitch = prefs.getVoicePitch()

    init {
        mapAnimalNoises()
    }

    private fun mapAnimalNoises() {
        for (an in animal_sounds) {
            if (animalNoiseMap[an.animal] == null) {
                animalNoiseMap[an.animal] = mutableListOf()
            }
            animalNoiseMap[an.animal]?.add(an)
        }
    }

    fun queueAnimalNoise(animal: Animal) {
        currentAnimalNoise = getRandomAnimalNoise(animal)
    }

    /** Re-read voice settings from prefs (e.g. after returning from Settings). */
    fun reloadVoiceSettings() {
        voiceSpeed = prefs.getVoiceSpeed()
        voicePitch = prefs.getVoicePitch()
    }

    fun saveVoiceOptions(voices: List<String>) {
        prefs.setVoiceOptions(voices)
    }

    fun getSelectedVoiceName(): String? {
        return prefs.getSelectedVoiceName()
    }

    fun getRandomAnimalNoise(animal: Animal): AnimalNoise? {
        val availableNoiseCount: Int = animalNoiseMap[animal]?.size ?: 0
        Timber.d("there are $availableNoiseCount sounds for ${animal.name}")
        return animalNoiseMap[animal]?.randomOrNull()
    }

    companion object {
        val TAG: String = MainViewModel::class.java.simpleName
    }
}