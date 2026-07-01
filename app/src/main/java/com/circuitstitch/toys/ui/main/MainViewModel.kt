package com.circuitstitch.toys.ui.main

import androidx.lifecycle.ViewModel
import com.circuitstitch.toys.Announcer
import com.circuitstitch.toys.RealAnnouncer
import com.circuitstitch.toys.models.Animal
import com.circuitstitch.toys.models.AnimalNoise.Companion.animal_sounds

/**
 * On tap, picks a *random* recorded clip for the animal and hands it to the [Announcer], which
 * speaks the animal's name (when enabled) then plays the clip. TTS + MediaPlayer live behind the
 * Announcer seam, so this stays a plain unit-testable object (tests pass a fake Announcer).
 */
class MainViewModel(private val announcer: Announcer = RealAnnouncer()) : ViewModel() {
    private val noisesByAnimal = animal_sounds.groupBy { it.animal }

    fun play(animal: Animal) {
        val noise = noisesByAnimal[animal]?.randomOrNull() ?: return
        announcer.announce(noise)
    }

    override fun onCleared() {
        announcer.release()
    }
}
