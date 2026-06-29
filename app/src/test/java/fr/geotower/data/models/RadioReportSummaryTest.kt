package fr.geotower.data.models

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RadioReportSummaryTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        // formatFrequency() s'appuie sur la locale par défaut : on la fige pour un test déterministe.
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun marker(
        actorLabel: String? = null,
        serviceMask: Int = RadioServiceMasks.BROADCAST,
        minFreqKhz: Int? = null,
        maxFreqKhz: Int? = null,
        detailText: String? = null
    ) = RadioMapMarker(
        id = "1",
        latitude = 48.0,
        longitude = 2.0,
        serviceMask = serviceMask,
        systemMask = 0,
        actorLabel = actorLabel,
        emitterCount = 0,
        antennaCount = 0,
        minFreqKhz = minFreqKhz,
        maxFreqKhz = maxFreqKhz,
        detailText = detailText
    )

    @Test
    fun summaryExposesEveryAvailableFieldInOrder() {
        val fields = marker(
            actorLabel = "Orange",
            detailText = "Systemes: FM, DAB\nFrequences: 87.5-108 MHz\nSupport: hauteur_dm=320"
        ).reportSummaryFields("Orange")

        assertEquals(
            listOf(
                RadioReportSummaryFields.NETWORK to "Orange",
                RadioReportSummaryFields.SYSTEMS to "FM, DAB",
                RadioReportSummaryFields.FREQUENCIES to "87.5-108 MHz",
                RadioReportSummaryFields.SUPPORT_HEIGHT to "32 m"
            ),
            fields.map { it.id to it.value }
        )
    }

    @Test
    fun summaryOmitsBlankFieldsButAlwaysKeepsNetwork() {
        val fields = marker(actorLabel = null, serviceMask = RadioServiceMasks.BROADCAST)
            .reportSummaryFields("Radio/TV")

        assertEquals(1, fields.size)
        assertEquals(RadioReportSummaryFields.NETWORK, fields.first().id)
        // Le libellé réseau fourni (non vide) garde le champ présent même sans autres détails.
        assertEquals("Radio/TV", fields.first().value)
    }

    @Test
    fun summaryFallsBackToFrequencyRangeWhenNoDetailFrequencies() {
        val fields = marker(
            actorLabel = "TDF",
            minFreqKhz = 87_500,
            maxFreqKhz = 108_000
        ).reportSummaryFields("TDF")

        val frequency = fields.firstOrNull { it.id == RadioReportSummaryFields.FREQUENCIES }?.value
        assertEquals("87.5 MHz-108.0 MHz", frequency)
    }
}
