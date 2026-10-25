package com.ahu.ahutong.personalization.journey

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JourneyFailureIsolationTest {
    @Test
    fun `all emitted journey labels are accepted by the trainer`() {
        assertTrue(JourneyTrainingLabelPolicy.accepts(JourneyTrainingLabelPolicy.ORGANIC_JOURNEY))
        assertTrue(JourneyTrainingLabelPolicy.accepts(JourneyTrainingLabelPolicy.INTERVENTION_FREE_TIMEOUT))
        assertTrue(JourneyTrainingLabelPolicy.accepts(JourneyTrainingLabelPolicy.INTERVENTION_FREE_MAX_STEPS))
        assertFalse(JourneyTrainingLabelPolicy.accepts("UNRECOGNIZED"))
    }
}
