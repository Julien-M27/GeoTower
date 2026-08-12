package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que « quelque chose a changé » veut dire en quittant l'édition d'une tournée : c'est cette
 * comparaison qui décide si l'on demande d'enregistrer ou si l'on sort sans rien demander.
 */
class TripContentComparisonTest {
    private val original = plan(ladder(3), legs = listOf(leg(0, 1), leg(1, 2)))

    @Test
    fun ignoresTheSaveTimestamp() {
        // Enregistrer met à jour `updatedAtMillis` : sans quoi le simple fait d'ouvrir la tournée
        // ferait croire à une modification et demanderait de confirmer un travail inexistant.
        assertTrue(original.hasSameContentAs(original.copy(updatedAtMillis = 9_999L)))
    }

    @Test
    fun ignoresTheComputedRoute() {
        // Les segments ne font que découler des étapes : leur arrivée n'est pas une modification
        // de l'utilisateur.
        assertTrue(original.hasSameContentAs(original.copy(legs = emptyList())))
    }

    @Test
    fun ignoresTheNameAndTheSchedule() {
        val renamed = original.copy(name = "Autre nom", plannedAtMillis = 1_000L)

        assertTrue(original.hasSameContentAs(renamed))
    }

    @Test
    fun seesAnAddedStep() {
        assertFalse(original.hasSameContentAs(plan(ladder(4))))
    }

    @Test
    fun seesAMovedStep() {
        val moved = original.copy(
            steps = original.steps.toMutableList().apply { set(1, step(49.0, 3.0)) }
        )

        assertFalse(original.hasSameContentAs(moved))
    }

    @Test
    fun seesAReorderedTour() {
        assertFalse(original.hasSameContentAs(original.copy(steps = original.steps.reversed())))
    }

    @Test
    fun seesACheckedStep() {
        val visited = original.copy(
            steps = original.steps.toMutableList().apply {
                set(0, this[0].copy(visitedAtMillis = 42L))
            }
        )

        assertFalse(original.hasSameContentAs(visited))
    }

    @Test
    fun seesAProfileOrLoopChange() {
        assertFalse(original.hasSameContentAs(original.copy(profile = RouteApi.PROFILE_PEDESTRIAN)))
        assertFalse(original.hasSameContentAs(original.copy(returnToStart = true)))
    }
}
