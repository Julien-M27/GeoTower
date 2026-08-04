package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Echelle du traitement local : elle est passee de 4 a 5 crans (insertion de « base en local,
 * pannes serveur »). Deux choses doivent tenir : le decalage des reglages deja enregistres, et le
 * fait que base et pannes soient desormais des axes independants.
 */
class LocalModeLevelScaleTest {

    @Test
    fun legacyLevelsShiftOneStepUp() {
        // 0 reste 0 ; les trois autres glissent d'un rang pour laisser la place au cran « base ».
        assertEquals(AppConfig.LOCAL_MODE_SERVER, AppConfig.migratedLocalModeLevel(0))
        assertEquals(AppConfig.LOCAL_MODE_OUTAGES, AppConfig.migratedLocalModeLevel(1))
        assertEquals(AppConfig.LOCAL_MODE_BOTH, AppConfig.migratedLocalModeLevel(2))
        assertEquals(AppConfig.LOCAL_MODE_MAX, AppConfig.migratedLocalModeLevel(3))
    }

    @Test
    fun migrationNeverOverflowsOrGoesNegative() {
        // Valeur absurde en base (profil importe, ecriture concurrente) : on reste dans l'echelle.
        assertEquals(AppConfig.LOCAL_MODE_MAX, AppConfig.migratedLocalModeLevel(9))
        assertEquals(AppConfig.LOCAL_MODE_SERVER, AppConfig.migratedLocalModeLevel(-3))
    }

    @Test
    fun databaseAndOutagesAreIndependentAxes() {
        // Le cran « base en local » ne doit surtout pas emporter les pannes : c'est tout l'objet
        // du 5e cran (l'ancienne echelle cumulative ne savait pas exprimer cet etat).
        assertTrue(AppConfig.levelBuildsDbLocally(AppConfig.LOCAL_MODE_DB))
        assertFalse(AppConfig.levelFetchesOutagesLocally(AppConfig.LOCAL_MODE_DB))

        assertFalse(AppConfig.levelBuildsDbLocally(AppConfig.LOCAL_MODE_OUTAGES))
        assertTrue(AppConfig.levelFetchesOutagesLocally(AppConfig.LOCAL_MODE_OUTAGES))

        listOf(AppConfig.LOCAL_MODE_BOTH, AppConfig.LOCAL_MODE_MAX).forEach { level ->
            assertTrue(AppConfig.levelBuildsDbLocally(level))
            assertTrue(AppConfig.levelFetchesOutagesLocally(level))
        }

        assertFalse(AppConfig.levelBuildsDbLocally(AppConfig.LOCAL_MODE_SERVER))
        assertFalse(AppConfig.levelFetchesOutagesLocally(AppConfig.LOCAL_MODE_SERVER))
    }
}
