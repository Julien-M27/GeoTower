package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSourcesTest {

    @Test
    fun allowedHostRequiresHttpsOfficialHostNoUserInfo() {
        assertTrue(OfficialSources.isAllowedHost(OfficialSources.MONTHLY_SUP_DATASET_API_URL))
        assertTrue(OfficialSources.isAllowedHost("https://object.files.data.gouv.fr/abc/new_sup.zip"))
        assertTrue(OfficialSources.isAllowedHost("https://data.anfr.fr/api/explore/v2.1/x"))

        assertFalse(OfficialSources.isAllowedHost("http://www.data.gouv.fr/x")) // pas HTTPS
        assertFalse(OfficialSources.isAllowedHost("https://evil.example.com/sup.zip")) // hote non autorise
        assertFalse(OfficialSources.isAllowedHost("https://user:pass@data.anfr.fr/x")) // userinfo
    }

    @Test
    fun selectsBothDataAndRefZipsOnAllowedHost() {
        val json = """
            {
              "resources": [
                {"url":"https://www.data.gouv.fr/docs/readme.pdf","format":"pdf","last_modified":"2026-07-02"},
                {"url":"https://static.data.gouv.fr/resources/x/20260530-export-etalab-data.zip","format":"zip","last_modified":"2026-06-02"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip","format":"zip","last_modified":"2026-07-02"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip","format":"zip","last_modified":"2026-07-02"}
              ]
            }
        """.trimIndent()

        val urls = OfficialSources.selectMonthlySupZipUrls(json)!!
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip", urls.dataUrl)
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip", urls.refUrl)
    }

    @Test
    fun picksLatestDataDateNotLatestUpload() {
        // Cas reel : data.gouv a re-uploade un VIEIL export (donnees mars 2022) avec un last_modified plus
        // recent que l'export courant (donnees juin 2026). Trier sur last_modified prenait le fichier 2022
        // (perime + Latin-1) -> on doit choisir par la DATE DES DONNEES (prefixe du nom de fichier).
        val json = """
            {
              "resources": [
                {"url":"https://static.data.gouv.fr/resources/x/20220331-export-etalab-data.zip","format":"zip","last_modified":"2026-07-03T07:23:57"},
                {"url":"https://static.data.gouv.fr/resources/x/20220331-export-etalab-ref.zip","format":"zip","last_modified":"2026-07-03T07:20:00"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip","format":"zip","last_modified":"2026-07-02T13:50:21"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip","format":"zip","last_modified":"2026-07-02T13:48:28"}
              ]
            }
        """.trimIndent()

        val urls = OfficialSources.selectMonthlySupZipUrls(json)!!
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip", urls.dataUrl)
        // La reference doit venir du MEME export (meme date de donnees), pas du ref 2022 re-uploade.
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip", urls.refUrl)
    }

    @Test
    fun picksModernYyyymmddOverLegacyDdmmyyyyFilename() {
        // Cas reel observe sur l'appareil : le dataset contient encore de TRES vieux exports (2015-2018)
        // dont le nom est en DDMMYYYY (ex. 31052018_export_etalab_data.zip = 31 mai 2018). Trier les
        // prefixes comme des CHAINES mettait "31052018" AVANT "20260630" ('3' > '2') -> l'app choisissait
        // le fichier de mai 2018 (32 Mo, ~53 000 sites manquants, Latin-1). La cle de tri doit normaliser
        // DDMMYYYY -> YYYYMMDD pour que l'export courant gagne.
        val json = """
            {
              "resources": [
                {"url":"https://static.data.gouv.fr/resources/x/31052018_export_etalab_data.zip","format":"zip","last_modified":"2018-06-05T14:07:19"},
                {"url":"https://static.data.gouv.fr/resources/x/31052018_export_etalab_ref.zip","format":"zip","last_modified":"2018-06-05T14:06:27"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip","format":"zip","last_modified":"2026-07-02T13:50:21"},
                {"url":"https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip","format":"zip","last_modified":"2026-07-02T13:48:28"}
              ]
            }
        """.trimIndent()

        val urls = OfficialSources.selectMonthlySupZipUrls(json)!!
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-data.zip", urls.dataUrl)
        assertEquals("https://static.data.gouv.fr/resources/x/20260630-export-etalab-ref.zip", urls.refUrl)
    }

    @Test
    fun dataDateKeyNormalizesLegacyAndModernFilenames() {
        // Moderne YYYYMMDD : inchange.
        assertEquals("20260630", OfficialSources.dataDateKey("20260630-export-etalab-data.zip"))
        // Ancien DDMMYYYY : reordonne en YYYYMMDD (31/05/2018 -> 20180531).
        assertEquals("20180531", OfficialSources.dataDateKey("31052018_export_etalab_data.zip"))
        // Pas de date en tete : chaine vide (ne gagne jamais le tri).
        assertEquals("", OfficialSources.dataDateKey("tables_supports_antennes_emetteurs_bandes.zip"))
        assertEquals("", OfficialSources.dataDateKey("export_etalab_data_22122017.zip"))
    }

    @Test
    fun returnsNullWhenNoZipOrDisallowedHostOrBadJson() {
        assertNull(
            OfficialSources.selectMonthlySupZipUrls(
                """{"resources":[{"url":"https://evil.example.com/etalab-data.zip","format":"zip"}]}""",
            ),
        )
        assertNull(OfficialSources.selectMonthlySupZipUrls("not json"))
        assertNull(OfficialSources.selectMonthlySupZipUrls("""{"resources":[]}"""))
    }

    @Test
    fun resolvesObservatoireCsvUrlFromExportHtml() {
        // La page contient des archives (chemin /AAAA/MM/JJ/uuid/) AVANT le fichier courant
        // (chemin dataset/{timestamp}_observatoireod_{date}.csv) : on doit ignorer les archives.
        val html = """
            archive https:\/\/data.anfr.fr\/sites\/default\/files\/dataset\/2025\/05\/02\/dd11fac6-4531-4a27-9c8c-a3a9e4ec2107\/observatoireod_20250501.csv
            &quot;file_csv&quot;,&quot;value&quot;:&quot;https:\/\/data.anfr.fr\/sites\/default\/files\/dataset\/20260702170329_observatoireod_20260702.csv&quot;\} more
        """.trimIndent()

        assertEquals(
            "https://data.anfr.fr/sites/default/files/dataset/20260702170329_observatoireod_20260702.csv",
            OfficialSources.resolveObservatoireCsvUrl(html),
        )
        assertNull(OfficialSources.resolveObservatoireCsvUrl("no observatory csv here"))
    }

    @Test
    fun extractsQuarterFromArcepFileNames() {
        assertEquals("2026-T2", OfficialSources.extractQuarter("sites_2026_T2.csv"))
        assertEquals("2025-T3", OfficialSources.extractQuarter("T3_2025_sites.csv"))
        assertNull(OfficialSources.extractQuarter("sites.csv"))
    }

    @Test
    fun selectsLatestQuarterArcepCsv() {
        val urls = listOf(
            "https://data.arcep.fr/mobile/sites/2026_T1_sites.csv",
            "https://data.arcep.fr/mobile/sites/2026_T2_sites.csv",
            "https://data.arcep.fr/mobile/sites/readme.txt",
            "https://evil.example.com/2026_T4_sites.csv",
        )
        assertEquals(
            "https://data.arcep.fr/mobile/sites/2026_T2_sites.csv",
            OfficialSources.selectLatestArcepCsvUrl(urls),
        )
    }
}
