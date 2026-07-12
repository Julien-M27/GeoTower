package fr.geotower.data.build

import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parite de la classification radio avec le builder serveur (`fr_radio_db_builder.py`). */
class RadioClassifierTest {

    @Test
    fun mobileLikeSystemsAreRecognised() {
        assertTrue(RadioClassifier.isMobileLikeSystem("GSM 900"))
        assertTrue(RadioClassifier.isMobileLikeSystem("GSM 1800"))
        assertTrue(RadioClassifier.isMobileLikeSystem("UMTS 2100"))
        assertTrue(RadioClassifier.isMobileLikeSystem("5G NR 3500"))
        assertTrue(RadioClassifier.isMobileLikeSystem("LTE 800"))
    }

    @Test
    fun privateLteAndNonMobileAreNotMobileLike() {
        // "LTE P..." (reseau prive) n'est PAS mobile-like (le " P" l'exclut).
        assertFalse(RadioClassifier.isMobileLikeSystem("LTE P 400"))
        assertFalse(RadioClassifier.isMobileLikeSystem("GSM R"))
        assertFalse(RadioClassifier.isMobileLikeSystem("FM"))
        assertFalse(RadioClassifier.isMobileLikeSystem("RDF DVB-T"))
        assertFalse(RadioClassifier.isMobileLikeSystem("FH"))
    }

    @Test
    fun publicMobileOperatorsAreDetectedBySubstring() {
        assertTrue(RadioClassifier.isPublicMobileOperator("ORANGE FRANCE"))
        assertTrue(RadioClassifier.isPublicMobileOperator("Free Mobile"))
        assertTrue(RadioClassifier.isPublicMobileOperator("SOCIETE FRANCAISE DU RADIOTELEPHONE - SFR"))
        assertFalse(RadioClassifier.isPublicMobileOperator("TDF"))
        assertFalse(RadioClassifier.isPublicMobileOperator("SNCF RESEAU"))
        assertFalse(RadioClassifier.isPublicMobileOperator("MINISTERE DE LA DEFENSE"))
    }

    @Test
    fun systemKeyMapsPrefixes() {
        assertEquals("FM", RadioClassifier.systemKey("FM"))
        assertEquals("DVB_T", RadioClassifier.systemKey("RDF DVB-T"))
        assertEquals("DAB", RadioClassifier.systemKey("RDF T-DAB"))
        assertEquals("AM", RadioClassifier.systemKey("RDF AM"))
        assertEquals("FH", RadioClassifier.systemKey("FH 38 GHz"))
        assertEquals("GSM_R", RadioClassifier.systemKey("GSM R"))
        assertEquals("TETRA", RadioClassifier.systemKey("TETRAPOL"))
        assertEquals("POCSAG", RadioClassifier.systemKey("RMU"))
        assertEquals("RADAR", RadioClassifier.systemKey("RDR METEO"))
        assertEquals("LTE_PRIVATE", RadioClassifier.systemKey("LTE P 400"))
        assertEquals("METEO_RS", RadioClassifier.systemKey("RS"))
        assertEquals("TELEMETRY", RadioClassifier.systemKey("TELEM"))
        assertEquals("OTHER", RadioClassifier.systemKey("QUELQUE CHOSE"))
    }

    @Test
    fun systemBitMatchesModelConstants() {
        assertEquals(RadioSystemMasks.FM, RadioClassifier.systemBit("FM"))
        assertEquals(RadioSystemMasks.DAB, RadioClassifier.systemBit("DAB"))
        assertEquals(RadioSystemMasks.GSM_R, RadioClassifier.systemBit("GSM_R"))
        assertEquals(RadioSystemMasks.OTHER, RadioClassifier.systemBit("INEXISTANT"))
    }

    @Test
    fun serviceForFollowsEvaluationOrder() {
        assertEquals(RadioServiceMasks.FH, RadioClassifier.serviceFor("FH 38 GHz", ""))
        assertEquals(RadioServiceMasks.BROADCAST, RadioClassifier.serviceFor("FM", ""))
        assertEquals(RadioServiceMasks.BROADCAST, RadioClassifier.serviceFor("RDF T-DAB", ""))
        assertEquals(RadioServiceMasks.RAIL, RadioClassifier.serviceFor("GSM R", ""))
        // Un acteur SNCF prime sur la classification systeme (LTE prive -> ferroviaire).
        assertEquals(RadioServiceMasks.RAIL, RadioClassifier.serviceFor("LTE P 400", "SNCF RESEAU"))
        assertEquals(RadioServiceMasks.TRANSPORT, RadioClassifier.serviceFor("COM TER", ""))
        assertEquals(RadioServiceMasks.SATELLITE, RadioClassifier.serviceFor("SAT COM", ""))
        assertEquals(RadioServiceMasks.RADAR, RadioClassifier.serviceFor("RDR", ""))
        assertEquals(RadioServiceMasks.PRIVATE, RadioClassifier.serviceFor("PMR", ""))
        assertEquals(RadioServiceMasks.OTHER, RadioClassifier.serviceFor("SYSTEME INCONNU", ""))
    }

    @Test
    fun serviceNamesJoinsLabelsInBitOrder() {
        assertEquals("Radio/TV", RadioClassifier.serviceNames(RadioServiceMasks.BROADCAST))
        assertEquals(
            "Radio/TV, Ferroviaire",
            RadioClassifier.serviceNames(RadioServiceMasks.BROADCAST or RadioServiceMasks.RAIL),
        )
        assertEquals("Autres", RadioClassifier.serviceNames(RadioServiceMasks.OTHER))
    }
}
