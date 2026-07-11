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
