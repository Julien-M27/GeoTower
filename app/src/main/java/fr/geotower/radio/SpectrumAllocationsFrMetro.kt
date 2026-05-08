package fr.geotower.radio

object SpectrumAllocationsFrMetro {
    const val ARCEP_SPECTRUM_SOURCE =
        "https://www.arcep.fr/la-regulation/grands-dossiers-reseaux-mobiles/la-couverture-mobile-en-metropole/le-patrimoine-de-frequences-des-operateurs-mobiles.html"

    val allocations: List<SpectrumAllocation> = listOf(
        SpectrumAllocation(MobileOperator.SFR, bandLabel = "700", ratBandLte = "B28", ratBandNr = "n28", duplexMode = DuplexMode.FDD, uplinkStartMHz = 703.0, uplinkEndMHz = 708.0, downlinkStartMHz = 758.0, downlinkEndMHz = 763.0, bandwidthMHz = 5.0, validFrom = "2015-12-07", validTo = "2035-12-07", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "700", ratBandLte = "B28", ratBandNr = "n28", duplexMode = DuplexMode.FDD, uplinkStartMHz = 708.0, uplinkEndMHz = 718.0, downlinkStartMHz = 763.0, downlinkEndMHz = 773.0, bandwidthMHz = 10.0, validFrom = "2015-12-07", validTo = "2035-12-07", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "700", ratBandLte = "B28", ratBandNr = "n28", duplexMode = DuplexMode.FDD, uplinkStartMHz = 718.0, uplinkEndMHz = 723.0, downlinkStartMHz = 773.0, downlinkEndMHz = 778.0, bandwidthMHz = 5.0, validFrom = "2015-12-07", validTo = "2035-12-07", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "700", ratBandLte = "B28", ratBandNr = "n28", duplexMode = DuplexMode.FDD, uplinkStartMHz = 723.0, uplinkEndMHz = 733.0, downlinkStartMHz = 778.0, downlinkEndMHz = 788.0, bandwidthMHz = 10.0, validFrom = "2015-12-07", validTo = "2035-12-07", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),

        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "800", ratBandLte = "B20", duplexMode = DuplexMode.FDD, uplinkStartMHz = 832.0, uplinkEndMHz = 842.0, downlinkStartMHz = 791.0, downlinkEndMHz = 801.0, bandwidthMHz = 10.0, validFrom = "2012-01-16", validTo = "2032-01-16", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.SFR, bandLabel = "800", ratBandLte = "B20", duplexMode = DuplexMode.FDD, uplinkStartMHz = 842.0, uplinkEndMHz = 852.0, downlinkStartMHz = 801.0, downlinkEndMHz = 811.0, bandwidthMHz = 10.0, validFrom = "2012-01-16", validTo = "2032-01-16", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "800", ratBandLte = "B20", duplexMode = DuplexMode.FDD, uplinkStartMHz = 852.0, uplinkEndMHz = 862.0, downlinkStartMHz = 811.0, downlinkEndMHz = 821.0, bandwidthMHz = 10.0, validFrom = "2012-01-16", validTo = "2032-01-16", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),

        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "900", ratBandLte = "B8", duplexMode = DuplexMode.FDD, uplinkStartMHz = 880.1, uplinkEndMHz = 888.8, downlinkStartMHz = 925.1, downlinkEndMHz = 933.8, bandwidthMHz = 8.7, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "900", ratBandLte = "B8", duplexMode = DuplexMode.FDD, uplinkStartMHz = 888.8, uplinkEndMHz = 897.5, downlinkStartMHz = 933.8, downlinkEndMHz = 942.5, bandwidthMHz = 8.7, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "900", ratBandLte = "B8", duplexMode = DuplexMode.FDD, uplinkStartMHz = 897.5, uplinkEndMHz = 906.2, downlinkStartMHz = 942.5, downlinkEndMHz = 951.2, bandwidthMHz = 8.7, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.SFR, bandLabel = "900", ratBandLte = "B8", duplexMode = DuplexMode.FDD, uplinkStartMHz = 906.2, uplinkEndMHz = 914.9, downlinkStartMHz = 951.2, downlinkEndMHz = 959.9, bandwidthMHz = 8.7, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),

        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "1800", ratBandLte = "B3", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1710.0, uplinkEndMHz = 1730.0, downlinkStartMHz = 1805.0, downlinkEndMHz = 1825.0, bandwidthMHz = 20.0, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.SFR, bandLabel = "1800", ratBandLte = "B3", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1730.0, uplinkEndMHz = 1750.0, downlinkStartMHz = 1825.0, downlinkEndMHz = 1845.0, bandwidthMHz = 20.0, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "1800", ratBandLte = "B3", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1750.0, uplinkEndMHz = 1765.0, downlinkStartMHz = 1845.0, downlinkEndMHz = 1860.0, bandwidthMHz = 15.0, validFrom = "2014-10-11", validTo = "2031-10-11", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "1800", ratBandLte = "B3", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1765.0, uplinkEndMHz = 1785.0, downlinkStartMHz = 1860.0, downlinkEndMHz = 1880.0, bandwidthMHz = 20.0, validFrom = "2018-12-08", validTo = "2034-12-08", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),

        SpectrumAllocation(MobileOperator.SFR, bandLabel = "2100", ratBandLte = "B1", ratBandNr = "n1", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1920.5, uplinkEndMHz = 1935.3, downlinkStartMHz = 2110.5, downlinkEndMHz = 2125.3, bandwidthMHz = 14.8, validFrom = "2018-12-11", validTo = "2032-12-11", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "2100", ratBandLte = "B1", ratBandNr = "n1", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1935.3, uplinkEndMHz = 1950.1, downlinkStartMHz = 2125.3, downlinkEndMHz = 2140.1, bandwidthMHz = 14.8, validFrom = "2018-12-11", validTo = "2032-12-11", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "2100", ratBandLte = "B1", ratBandNr = "n1", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1950.1, uplinkEndMHz = 1964.9, downlinkStartMHz = 2140.1, downlinkEndMHz = 2154.9, bandwidthMHz = 14.8, validFrom = "2018-12-11", validTo = "2032-12-11", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "2100", ratBandLte = "B1", ratBandNr = "n1", duplexMode = DuplexMode.FDD, uplinkStartMHz = 1964.9, uplinkEndMHz = 1979.7, downlinkStartMHz = 2154.9, downlinkEndMHz = 2169.7, bandwidthMHz = 14.8, validFrom = "2018-12-11", validTo = "2032-12-11", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 90),

        SpectrumAllocation(MobileOperator.SFR, bandLabel = "2600", ratBandLte = "B7", duplexMode = DuplexMode.FDD, uplinkStartMHz = 2500.0, uplinkEndMHz = 2515.0, downlinkStartMHz = 2620.0, downlinkEndMHz = 2635.0, bandwidthMHz = 15.0, validFrom = "2011-10-10", validTo = "2031-10-10", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "2600", ratBandLte = "B7", duplexMode = DuplexMode.FDD, uplinkStartMHz = 2515.0, uplinkEndMHz = 2535.0, downlinkStartMHz = 2635.0, downlinkEndMHz = 2655.0, bandwidthMHz = 20.0, validFrom = "2011-10-10", validTo = "2031-10-10", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "2600", ratBandLte = "B7", duplexMode = DuplexMode.FDD, uplinkStartMHz = 2535.0, uplinkEndMHz = 2550.0, downlinkStartMHz = 2655.0, downlinkEndMHz = 2670.0, bandwidthMHz = 15.0, validFrom = "2011-10-10", validTo = "2031-10-10", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "2600", ratBandLte = "B7", duplexMode = DuplexMode.FDD, uplinkStartMHz = 2550.0, uplinkEndMHz = 2570.0, downlinkStartMHz = 2670.0, downlinkEndMHz = 2690.0, bandwidthMHz = 20.0, validFrom = "2011-10-10", validTo = "2031-10-10", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),

        SpectrumAllocation(MobileOperator.SFR, bandLabel = "3500", ratBandNr = "n78", duplexMode = DuplexMode.TDD, tddStartMHz = 3490.0, tddEndMHz = 3570.0, bandwidthMHz = 80.0, validFrom = "2020-11-17", validTo = "2035-11-17", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.BOUYGUES, bandLabel = "3500", ratBandNr = "n78", duplexMode = DuplexMode.TDD, tddStartMHz = 3570.0, tddEndMHz = 3640.0, bandwidthMHz = 70.0, validFrom = "2020-11-17", validTo = "2035-11-17", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.FREE, bandLabel = "3500", ratBandNr = "n78", duplexMode = DuplexMode.TDD, tddStartMHz = 3640.0, tddEndMHz = 3710.0, bandwidthMHz = 70.0, validFrom = "2020-11-17", validTo = "2035-11-17", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95),
        SpectrumAllocation(MobileOperator.ORANGE, bandLabel = "3500", ratBandNr = "n78", duplexMode = DuplexMode.TDD, tddStartMHz = 3710.0, tddEndMHz = 3800.0, bandwidthMHz = 90.0, validFrom = "2020-11-17", validTo = "2035-11-17", sourceName = "Arcep patrimoine de frequences mobiles", sourceUrl = ARCEP_SPECTRUM_SOURCE, confidence = 95)
    )

    fun find(operator: MobileOperator, technology: RadioTechnology, bandLabel: String): SpectrumAllocation? {
        val normalizedBand = normalizeBandLabel(bandLabel)
        return allocations.firstOrNull { allocation ->
            allocation.operator == operator &&
                allocation.bandLabel == normalizedBand &&
                when (technology) {
                    RadioTechnology.LTE_4G -> allocation.ratBandLte != null
                    RadioTechnology.NR_5G -> allocation.ratBandNr != null
                    else -> false
                }
        }
    }

    fun normalizeBandLabel(raw: String): String {
        return when {
            raw.contains("3500") || raw.contains("3.5") || raw.contains("3,5") || raw.contains("n78", ignoreCase = true) -> "3500"
            else -> raw.filter { it.isDigit() }
        }
    }
}
