package fr.geotower.ui.screens.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLocalBuildReportTest {

    @Test
    fun formatLocalBuildReportIncludesTitleSummaryAndAllDetails() {
        val report = formatLocalBuildReport(
            title = "Génération locale (mesures)",
            summary = "Dernière génération mesurée le 2026-11-26 21:50",
            details = listOf(
                "Durée totale : 42 s",
                "Pic tas Java : 185 Mo",
                "Base produite : 120 Mo"
            )
        )

        assertEquals(
            """
            Génération locale (mesures)
            Dernière génération mesurée le 2026-11-26 21:50
            Durée totale : 42 s
            Pic tas Java : 185 Mo
            Base produite : 120 Mo
            """.trimIndent(),
            report
        )
    }
}
