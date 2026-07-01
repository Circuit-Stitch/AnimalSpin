package com.circuitstitch.toys

import com.circuitstitch.toys.models.Animal
import com.circuitstitch.toys.models.AnimalNoise
import com.circuitstitch.toys.ui.main.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the clip-selection seam offline: with a fake [Announcer] in place, no Android speech
 * or media engine is touched, so tapping an animal is a plain JVM assertion.
 */
class MainViewModelTest {

    private class FakeAnnouncer : Announcer {
        val announced = mutableListOf<AnimalNoise>()
        override fun announce(noise: AnimalNoise) { announced.add(noise) }
        override fun release() {}
    }

    @Test
    fun `play announces a clip belonging to the tapped animal`() {
        val fake = FakeAnnouncer()

        MainViewModel(fake).play(Animal.CAT)

        assertEquals(1, fake.announced.size)
        assertEquals(Animal.CAT, fake.announced.single().animal)
    }

    @Test
    fun `every selected clip belongs to the tapped animal, across many taps`() {
        val fake = FakeAnnouncer()
        val vm = MainViewModel(fake)

        repeat(50) { vm.play(Animal.DOG) }

        assertEquals(50, fake.announced.size)
        assertTrue(fake.announced.all { it.animal == Animal.DOG })
    }
}
