package fr.geotower.ui.components

import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lecture de la grille voix/data d'une fiche site, et surtout de la case « incertaine » : les relevés
 * opérateurs sont souvent partiels (data HS sans la voix, ou « Data : HS » global sans détail par
 * génération) et le tiret « rien de publié » s'y lisait comme une absence de technologie.
 */
class SiteServiceStatusGridTest {

    private fun site(
        voix2g: String? = null,
        voix3g: String? = null,
        voix4g: String? = null,
        voix5g: String? = null,
        data2g: String? = null,
        data3g: String? = null,
        data4g: String? = null,
        data5g: String? = null,
        voixGlobal: String? = null,
        dataGlobal: String? = null,
    ) = SiteHsEntity(
        idAnfr = "0000000001",
        operateur = "Orange",
        latitude = 48.85,
        longitude = 2.35,
        voix2g = voix2g,
        voix3g = voix3g,
        voix4g = voix4g,
        voix5g = voix5g,
        data2g = data2g,
        data3g = data3g,
        data4g = data4g,
        data5g = data5g,
        voixGlobal = voixGlobal,
        dataGlobal = dataGlobal,
    )

    /** Grille d'un site 2G/3G/4G en service (aucune génération en projet). */
    private fun gridOf(hsEntity: SiteHsEntity?, has5G: Boolean = false) = siteServiceStatusGrid(
        hsEntity = hsEntity,
        has2G = true,
        has3G = true,
        has4G = true,
        has5G = has5G,
        is2gProject = false,
        is3gProject = false,
        is4gProject = false,
        is5gProject = false,
    )

    @Test
    fun aDataOutageMakesTheUndeclaredVoiceOfTheSameGenerationUncertain() {
        val grid = gridOf(site(data4g = "HS"))

        val g4 = grid.getValue("4G")
        assertEquals(false, g4.isInternetOk)
        // Voix 4G non publiée alors que la data 4G est tombée : « ? », pas un tiret.
        assertNull(g4.isVoixOk)
        assertTrue(g4.isVoixUncertain)
        // La 3G n'est pas concernée : rien de publié, rien de déduit d'une autre génération.
        assertFalse(grid.getValue("3G").isVoixUncertain)
        assertFalse(grid.getValue("3G").isInternetUncertain)
    }

    @Test
    fun aGlobalServiceOutageMakesEveryUndeclaredGenerationOfThatServiceUncertain() {
        // Relevé sans aucun détail par génération : seul « Data : HS » est publié.
        val grid = gridOf(site(dataGlobal = "HS"))

        assertTrue(grid.getValue("3G").isInternetUncertain)
        assertTrue(grid.getValue("4G").isInternetUncertain)
        // La voix ne l'est pas : rien ne la déclare touchée.
        assertFalse(grid.getValue("3G").isVoixUncertain)
        assertFalse(grid.getValue("4G").isVoixUncertain)
    }

    @Test
    fun aServiceDeclaredUpGloballyKeepsNoDoubt() {
        // « Voix : OK, Data : HS » : l'opérateur a répondu pour la voix, on ne lui prête pas un doute.
        val grid = gridOf(site(voixGlobal = "OK", dataGlobal = "HS", data4g = "HS"))

        assertFalse(grid.getValue("4G").isVoixUncertain)
        assertFalse(grid.getValue("3G").isVoixUncertain)
        assertTrue(grid.getValue("3G").isInternetUncertain)
    }

    @Test
    fun aPublishedCodeIsNeverUncertain() {
        val grid = gridOf(site(voix4g = "OK", data4g = "HS", dataGlobal = "HS", voixGlobal = "HS"))

        val g4 = grid.getValue("4G")
        assertEquals(true, g4.isVoixOk)
        assertFalse(g4.isVoixUncertain)
        assertEquals(false, g4.isInternetOk)
        assertFalse(g4.isInternetUncertain)
    }

    @Test
    fun degradedCountsAsDownForTheDoubtToo() {
        val grid = gridOf(site(data4g = "DE"))

        // « DE » vaut rouge dans la grille : il doit aussi ouvrir le doute sur la voix 4G.
        assertEquals(false, grid.getValue("4G").isInternetOk)
        assertTrue(grid.getValue("4G").isVoixUncertain)
    }

    @Test
    fun anAbsentTechnologyStaysNeutral() {
        // 5G absente de la base : une data 5G déclarée HS ne peut pas rendre la 5G incertaine.
        val grid = gridOf(site(data5g = "HS"), has5G = false)

        val g5 = grid.getValue("5G")
        assertNull(g5.isVoixOk)
        assertFalse(g5.isVoixUncertain)
        assertFalse(g5.isInternetUncertain)
    }

    @Test
    fun aSiteWithoutAnyDeclaredOutageStaysGreen() {
        val grid = gridOf(null)

        grid.values.forEach { status ->
            assertFalse(status.isVoixUncertain)
            assertFalse(status.isInternetUncertain)
        }
        assertEquals(true, grid.getValue("4G").isVoixOk)
        assertEquals(true, grid.getValue("4G").isInternetOk)
    }

    @Test
    fun placeholderCodesAreNotReadAsPublished() {
        // « - » et « null » textuels ne sont pas des réponses : le doute reste ouvert.
        val grid = gridOf(site(voix4g = "-", data4g = "HS", voixGlobal = "null"))

        assertNull(grid.getValue("4G").isVoixOk)
        assertTrue(grid.getValue("4G").isVoixUncertain)
    }
}
