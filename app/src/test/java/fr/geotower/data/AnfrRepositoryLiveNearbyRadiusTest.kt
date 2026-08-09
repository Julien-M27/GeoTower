package fr.geotower.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnfrRepositoryLiveNearbyRadiusTest {
    @Test
    fun escalationStartsWithRemoteLimitSoNominalCaseCostsOneRequest() {
        val radii = liveNearbyRadiusEscalationKm(50.0)

        assertEquals(50.0, radii.first(), 0.0)
    }

    @Test
    fun escalationWidensUntilWholeEarthIsCovered() {
        val radii = liveNearbyRadiusEscalationKm(50.0)

        assertEquals(listOf(50.0, 200.0, 1_000.0, 20_100.0), radii)
    }

    @Test
    fun escalationDropsFallbackRadiiAlreadyCoveredByRemoteLimit() {
        val radii = liveNearbyRadiusEscalationKm(500.0)

        assertEquals(listOf(500.0, 1_000.0, 20_100.0), radii)
    }

    @Test
    fun escalationKeepsSingleRadiusWhenRemoteLimitIsAlreadyGlobal() {
        val radii = liveNearbyRadiusEscalationKm(30_000.0)

        assertEquals(listOf(30_000.0), radii)
    }

    @Test
    fun escalationGuardsAgainstZeroRadius() {
        val radii = liveNearbyRadiusEscalationKm(0.0)

        assertEquals(listOf(1.0, 200.0, 1_000.0, 20_100.0), radii)
    }

    // Si le serveur rogne le rayon (plafond plus bas que celui demande), elargir rejouerait la
    // meme requete plafonnee pour la meme reponse vide : l'escalade doit s'arreter la.
    @Test
    fun serverClampIsDetectedFromEchoedRadius() {
        assertTrue(liveNearbyRadiusWasClampedByServer(requestedKm = 200.0, effectiveKm = 50.0))
    }

    @Test
    fun honoredRadiusIsNotTakenForAClamp() {
        assertFalse(liveNearbyRadiusWasClampedByServer(requestedKm = 200.0, effectiveKm = 200.0))
    }

    @Test
    fun floatNoiseOnEchoedRadiusIsNotTakenForAClamp() {
        assertFalse(liveNearbyRadiusWasClampedByServer(requestedKm = 200.0, effectiveKm = 199.9999))
    }

    @Test
    fun missingEchoedRadiusKeepsEscalating() {
        assertFalse(liveNearbyRadiusWasClampedByServer(requestedKm = 200.0, effectiveKm = null))
    }
}
