package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorColorsTest {
    @Test
    fun searchLabelsFor_resolvesOverseasAliasesFromDefaultLabel() {
        val labels = OperatorColors.searchLabelsFor("Outremer Telecom")

        assertTrue(labels.contains("OUTREMER TELECOM"))
        assertTrue(labels.contains("SFR CARAIBE"))
        assertTrue(labels.contains(OperatorColors.OUTREMER_TELECOM_KEY))
    }

    @Test
    fun searchLabelsFor_returnsFallbackForUnknownOperator() {
        assertEquals(listOf("Unknown Mobile"), OperatorColors.searchLabelsFor(" Unknown Mobile "))
    }

    @Test
    fun operatorLogos_resolvesTdfLogo() {
        assertEquals(fr.geotower.R.drawable.logo_tdf, OperatorLogos.drawableRes("TDF"))
        assertEquals(fr.geotower.R.drawable.logo_tdf, OperatorLogos.drawableRes("tdf"))
        assertEquals(fr.geotower.R.drawable.logo_tdf, OperatorLogos.drawableRes("Télédiffusion de France"))
        assertEquals(fr.geotower.R.drawable.logo_tdf, OperatorLogos.drawableRes("TELEDIFFUSION DE FRANCE"))
    }

    @Test
    fun operatorLogos_resolvesTowercastLogo() {
        assertEquals(fr.geotower.R.drawable.logo_towercast, OperatorLogos.drawableRes("TOWERCAST"))
        assertEquals(fr.geotower.R.drawable.logo_towercast, OperatorLogos.drawableRes("towercast"))
        assertEquals(fr.geotower.R.drawable.logo_towercast, OperatorLogos.drawableRes("Tower Cast"))
    }

    @Test
    fun keysFor_resolvesOverseasAnfrVariantsAndCombinedLabels() {
        assertEquals(listOf(OperatorColors.ORANGE_KEY), OperatorColors.keysFor("ORANGE CARAIBE"))
        assertEquals(listOf(OperatorColors.SFR_KEY), OperatorColors.keysFor("SFR MAYOTTE"))
        assertEquals(listOf(OperatorColors.OUTREMER_TELECOM_KEY), OperatorColors.keysFor("SFR CARAIBE"))
        assertEquals(listOf(OperatorColors.FREE_CARAIBE_KEY), OperatorColors.keysFor("FREE CARAIBE"))
        assertEquals(listOf(OperatorColors.DIGICEL_KEY), OperatorColors.keysFor("DIGICEL AFG"))
        assertEquals(listOf(OperatorColors.OPT_NC_KEY), OperatorColors.keysFor("GOUV NELLE CALEDONIE (OPT)"))
        assertEquals(
            listOf(OperatorColors.ORANGE_KEY, OperatorColors.DIGICEL_KEY),
            OperatorColors.keysFor("ORANGE CARAIBE, DIGICEL")
        )
    }
}
