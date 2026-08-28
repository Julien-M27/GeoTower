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
        assertEquals(UncertainServiceColor.Red, g4.voixUncertainColor)
        assertFalse(g4.isVoixUncertainGreen)
        // La 3G n'est pas concernée : rien de publié, rien de déduit d'une autre génération.
        assertFalse(grid.getValue("3G").isVoixUncertain)
        assertFalse(grid.getValue("3G").isInternetUncertain)
    }

    @Test
    fun anOkSiblingMakesTheUndeclaredServiceGreenUncertain() {
        // La 3G est en panne, mais la data 4G est déclarée OK : la voix 4G non publiée reste une
        // information incertaine, avec un « ? » vert pour signaler que la génération fonctionne.
        val grid = gridOf(site(data3g = "HS", data4g = "OK"))

        val g4 = grid.getValue("4G")
        assertNull(g4.isVoixOk)
        assertEquals(UncertainServiceColor.Green, g4.voixUncertainColor)
        assertTrue(g4.isVoixUncertain)
        assertTrue(g4.isVoixUncertainGreen)
    }

    @Test
    fun aPublishedOkGenerationOverridesTheSiblingGlobalOutage() {
        // Le global Data peut être HS sur d'autres générations, mais la Data 5G précise est OK :
        // la Voix 5G non publiée est donc un « ? » vert.
        val grid = gridOf(site(data5g = "OK", dataGlobal = "HS"), has5G = true)

        val g5 = grid.getValue("5G")
        assertNull(g5.isVoixOk)
        assertEquals(UncertainServiceColor.Green, g5.voixUncertainColor)
        assertTrue(g5.isVoixUncertainGreen)
    }

    @Test
    fun aGlobalServiceOutageMakesEveryUndeclaredGenerationOfThatServiceUncertain() {
        // Relevé sans aucun détail par génération : seul « Data : HS » est publié.
        val grid = gridOf(site(dataGlobal = "HS"))

        assertTrue(grid.getValue("3G").isInternetUncertain)
        assertTrue(grid.getValue("4G").isInternetUncertain)
        // La Data globale est HS : une Voix non détaillée reste incertaine et doit être rouge.
        assertEquals(UncertainServiceColor.Red, grid.getValue("3G").voixUncertainColor)
        assertEquals(UncertainServiceColor.Red, grid.getValue("4G").voixUncertainColor)
    }

    @Test
    fun aGlobalOkDoesNotCloseTheDoubtBecauseItOnlyAggregatesWhatIsReported() {
        // Cas réel Orange : 2G/3G décommissionnées (« NE »), aucune colonne voix 4G, donc un
        // « voix : OK » de façade — alors que la data 4G est HS, donc la VoLTE avec elle.
        val grid = gridOf(
            site(
                voix2g = "NE",
                voix3g = "NE",
                voixGlobal = "OK",
                data3g = "NE",
                data4g = "HS",
                data5g = "HS",
                dataGlobal = "HS",
            ),
            has5G = true,
        )

        assertTrue(grid.getValue("4G").isVoixUncertain)
        assertTrue(grid.getValue("5G").isVoixUncertain)
        // La 2G et la 3G ont, elles, une réponse (« NE ») : tiret, pas de doute.
        assertFalse(grid.getValue("2G").isVoixUncertain)
        assertFalse(grid.getValue("3G").isVoixUncertain)
    }

    @Test
    fun aNotEquippedCodeIsAnAnswerNotADoubt() {
        // « NE » = non équipé : l'opérateur a répondu, la case reste un tiret neutre.
        val grid = gridOf(site(voix3g = "NE", data3g = "HS", dataGlobal = "HS"))

        assertNull(grid.getValue("3G").isVoixOk)
        assertFalse(grid.getValue("3G").isVoixUncertain)
    }

    @Test
    fun aGlobalHsOverridesAPublishedOkCode() {
        val grid = gridOf(site(voix4g = "OK", data4g = "HS", dataGlobal = "HS", voixGlobal = "HS"))

        val g4 = grid.getValue("4G")
        // Le HS global est prioritaire : même un OK détaillé est affiché rouge pour signaler
        // l'état global du service.
        assertEquals(false, g4.isVoixOk)
        assertFalse(g4.isVoixUncertain)
        assertEquals(false, g4.isInternetOk)
        assertFalse(g4.isInternetUncertain)
    }

    @Test
    fun aGlobalHsMakesEveryUndeclaredGenerationRed() {
        val grid = gridOf(site(voixGlobal = "HS", dataGlobal = "HS"), has5G = true)

        listOf("2G", "3G", "4G", "5G").forEach { technology ->
            val status = grid.getValue(technology)
            assertEquals(UncertainServiceColor.Red, status.voixUncertainColor)
            assertEquals(
                if (technology == "2G") null else UncertainServiceColor.Red,
                status.internetUncertainColor
            )
        }
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
    fun dataIsUnavailableOn2G() {
        // La Data 2G n'existe pas dans le tableau : même une valeur résiduelle ne doit ni afficher
        // un « ? », ni rendre la case rouge.
        val grid = gridOf(site(data2g = "HS", data3g = "HS"))

        val g2 = grid.getValue("2G")
        assertNull(g2.isInternetOk)
        assertNull(g2.internetUncertainColor)
        assertFalse(g2.isInternetUncertain)
        assertFalse(g2.isVoixUncertain)
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
