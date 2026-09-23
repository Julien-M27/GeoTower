package fr.geotower.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstStartScreenTest {

    @Test
    fun backFromAnIntermediateStepTargetsThePreviousStep() {
        assertEquals(2, previousOnboardingStep(3))
    }

    @Test
    fun backFromTheFirstStepHasNoPreviousStep() {
        assertNull(previousOnboardingStep(0))
    }
}
