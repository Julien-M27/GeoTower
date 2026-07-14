package fr.geotower.data.outages

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class OperatorCsvParserTest {

    @Test
    fun parsesWithPreambleQuotesAndNormalizedKeys() {
        val csv = "Export pannes SFR du jour\n" +
            "Code_Site_OP;Station_ANFR;Latitude;Longitude;Commune;Détail\n" +
            "SI0001;0751234567;48.8566;2.3522;PARIS;\"Incident; en cours\"\n" +
            "\n" +
            "SI0002;;48.86;2.34;LYON;Coupure\n"

        val rows = OperatorCsvParser.parse(csv.toByteArray(Charsets.UTF_8))

        assertEquals(2, rows.size) // préambule sauté, ligne vide filtrée
        assertEquals("SI0001", rows[0]["code_site_op"])
        assertEquals("0751234567", rows[0]["station_anfr"])
        assertEquals("PARIS", rows[0]["commune"])
        // ";" à l'intérieur d'un champ quoté préservé ; clé "Détail" normalisée en "detail".
        assertEquals("Incident; en cours", rows[0]["detail"])
        assertEquals("LYON", rows[1]["commune"])
    }

    @Test
    fun decodesWindows1252() {
        val csv = "code_site_op;commune\nSI1;Besançon\n"
        val rows = OperatorCsvParser.parse(csv.toByteArray(Charset.forName("windows-1252")))
        assertEquals("Besançon", rows[0]["commune"])
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertEquals(0, OperatorCsvParser.parse(ByteArray(0)).size)
    }
}
