package fr.geotower.data.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedtestOcrParserTest {

    @Test
    fun parseNperfFrenchCapture() {
        val text = """
            nPerf
            Orange F 5G NSA
            Réception
            482,5 Mb/s
            Envoi
            54,2 Mb/s
            Latence
            21 ms
            Gigue
            3,4 ms
            n78
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(482.5, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(54.2, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(21.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(3.4, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed.operatorName)
        assertEquals("5G NSA", parsed.networkTech)
        assertEquals("n78", parsed.band)
    }

    @Test
    fun parseOoklaCaptureWithGbpsAndKbps() {
        val text = """
            SPEEDTEST
            DOWNLOAD Mbps 1.25 Gbps
            UPLOAD Mbps 950 kbps
            Ping 14 ms
            Free Mobile 5G
            eNB: 405123
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(1250.0, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.95, parsed.uploadMbps ?: -1.0, 0.001)
        assertEquals(14.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals("Free", parsed.operatorName)
        assertEquals("5G", parsed.networkTech)
        assertEquals("405123", parsed.cellIdentifier)
    }

    @Test
    fun parseOoklaTwoColumnHeaderThenValues() {
        val text = """
            DOWNLOAD Mbps
            UPLOAD Mbps
            342.15
            48.70
            Ping ms
            18
            Jitter ms
            4
            Orange F
            5G
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(342.15, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(48.70, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(18.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(4.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed.operatorName)
        assertEquals("5G", parsed.networkTech)
    }

    @Test
    fun parseCellularFrCaptureWithPlmnAndStandaloneNumbers() {
        val text = """
            Cellular FR
            208-15
            NR NSA
            n78
            Descendant
            512,4
            Mbps
            Montant
            63,8
            Mbps
            Ping
            22
            ms
            eNB 405123
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(512.4, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(63.8, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(22.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals("Free", parsed.operatorName)
        assertEquals("5G NSA", parsed.networkTech)
        assertEquals("n78", parsed.band)
        assertEquals("405123", parsed.cellIdentifier)
    }

    @Test
    fun parseOoklaFrenchScreenshotWithSpacesAndAscendant() {
        val text = """
            SPEEDTEST
            Descendant Mbps
            Ascendant Mbps
            1 015
            95,1
            Ping ms 32 326 371 Gigue ms 12
            Orange
            SM-A5460
            5G
            BOUYGUES TELECO...
            Vélizy
            Quelle note donneriez-vous Orange?
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(1015.0, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(95.1, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(32.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(12.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed.operatorName)
        assertEquals("5G", parsed.networkTech)
    }

    @Test
    fun parseOoklaSplitUnitBelowNumberAndSameLineColumns() {
        val text1 = """
            SPEEDTEST
            Descendant
            86,7
            Mbps
            Ascendant
            1,35
            Mbps
            Ping ms 44 419 2093 Gigue ms 45
            Orange
            5G
        """.trimIndent()

        val parsed1 = SpeedtestOcrParser.parse(text1)
        assertEquals(86.7, parsed1.downloadMbps ?: -1.0, 0.01)
        assertEquals(1.35, parsed1.uploadMbps ?: -1.0, 0.01)
        assertEquals(44.0, parsed1.pingMs ?: -1.0, 0.01)
        assertEquals(45.0, parsed1.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed1.operatorName)
        assertEquals("5G", parsed1.networkTech)

        val text2 = """
            SPEEDTEST
            Descendant Mbps Ascendant Mbps
            225 4,17
            Ping ms 34 473 1079 Gigue ms 4
            Orange
            5G
        """.trimIndent()

        val parsed2 = SpeedtestOcrParser.parse(text2)
        assertEquals(225.0, parsed2.downloadMbps ?: -1.0, 0.01)
        assertEquals(4.17, parsed2.uploadMbps ?: -1.0, 0.01)
        assertEquals(34.0, parsed2.pingMs ?: -1.0, 0.01)
        assertEquals(4.0, parsed2.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed2.operatorName)
        assertEquals("5G", parsed2.networkTech)
    }

    @Test
    fun parseSignalQuestShareCard() {
        val text = """
            signal quest
            Speedtest · 24 novembre 2026 à 14:20
            Orange · 5G NSA · n78+B1+B3+B7
            ↓ DESCENDANT
            120
            Mb/s
            ↑ MONTANT
            0,6 Mb/s
            LATENCE MIN.
            41 ms
            Moyenne 104 ms
            ↓ DESCENDANT MAX 139 Mb/s
            ↑ MONTANT MAX 2,6 Mb/s
            ANCRE 4G eNB 55562 · PCI 425
            Sous charge ↓ 161 ms · gigue 83 ms
            Sous charge ↑ 554 ms · gigue 224 ms
            Gigue au repos 65 ms
            Serveur Poitiers · hébergé par Bouygues
            Samsung SM-A5460 · Le Mans
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(120.0, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.6, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(41.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(65.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsed.operatorName)
        assertEquals("5G NSA", parsed.networkTech)
        assertEquals("n78+B1+B3+B7", parsed.band)
        assertEquals("55562", parsed.cellIdentifier)

        // Cas où ML Kit lit les valeurs "MAX 139 Mb/s" et "MAX 2,6 Mb/s" avant "120 Mb/s" et "0,6 Mb/s"
        val rowByRow = """
            Orange · 5G NSA · n78+B1+B3+B7
            ↓ DESCENDANT
            ↓ DESCENDANT MAX 139 Mb/s
            120
            Mb/s
            ↑ MONTANT MAX 2,6 Mb/s
            ↑ MONTANT
            0,6
            Mb/s
            LATENCE MIN.
            41
            ms
            Gigue au repos 65 ms
            ANCRE 4G eNB 55562 · PCI 425
        """.trimIndent()
        val parsedRowByRow = SpeedtestOcrParser.parse(rowByRow)
        assertEquals(120.0, parsedRowByRow.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.6, parsedRowByRow.uploadMbps ?: -1.0, 0.01)
        assertEquals(41.0, parsedRowByRow.pingMs ?: -1.0, 0.01)
        assertEquals(65.0, parsedRowByRow.jitterMs ?: -1.0, 0.01)

        // Espaces autour de la virgule décimale (ex: "0, 6 Mb/s", "86, 7 Mbps")
        val spacedDecimals = SpeedtestOcrParser.parse("↓ DESCENDANT\n86, 7 Mbps\n↑ MONTANT\n0, 6 Mb/s")
        assertEquals(86.7, spacedDecimals.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.6, spacedDecimals.uploadMbps ?: -1.0, 0.01)
    }

    @Test
    fun parseInternationalOoklaFormatsAndUnknownOperator() {
        val text = """
            SPEEDTEST
            DOWNLOAD Mbps
            UPLOAD Mbps
            1,015.4
            124.8
            Ping ms 19
            Jitter ms 3
            5G
            How would you rate Singtel?
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(1015.4, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(124.8, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(19.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(3.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Singtel", parsed.operatorName)
        assertEquals("5G", parsed.networkTech)
    }

    @Test
    fun parseNperfScreenshot1WithServerLineAnd5GNsa() {
        val text = """
            nPerf
            Test de débit
            Auto: [FR] Anycast-FR - 100 Gb/s - Free
            Réception
            Max: 699.63 Mb/s
            Moy: 620.01 Mb/s
            Envoi
            Max: 174.09 Mb/s
            Moy: 163.08 Mb/s
            Latence
            Min: 21 ms
            Moy: 25 ms / Gig: 7 ms
            Connexion
            5G (NSA)
            Opérateur
            Lebara
            Réseau
            SFR
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(699.63, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(174.09, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(21.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(7.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Lebara", parsed.operatorName)
        assertEquals("5G NSA", parsed.networkTech)
    }

    @Test
    fun parseNperfScreenshot2RowByRowWithPlmnAndCell() {
        val text = """
            nPerf
            Auto: [FR] Anycast-FR - 10 Gb/s - Bouygues Teleco...
            Réception
            Envoi
            Latence
            Max: 0.03 Mb/s
            Max: 0.00 Mb/s
            Min: 30 ms
            Moy: 0.02 Mb/s
            Moy: 0.00 Mb/s
            Moy: 59 ms / Gig: 244
            Connexion
            4G+ (LTE-A)
            Opérateur
            La Poste Mobile
            Réseau
            Bouygues Telecom
            PLMN: 208:20 Cell: 517983:8 TAC: 32536 RSRP: -106
        """.trimIndent()

        val parsed = SpeedtestOcrParser.parse(text)
        assertEquals(0.03, parsed.downloadMbps ?: -1.0, 0.001)
        assertEquals(0.00, parsed.uploadMbps ?: -1.0, 0.001)
        assertEquals(30.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals("Bouygues Telecom", parsed.operatorName)
        assertEquals("4G+", parsed.networkTech)
        assertEquals("517983:8", parsed.cellIdentifier)
    }

    @Test
    fun parsePlmnAcrossCountries() {
        assertEquals("Swisscom", SpeedtestOcrParser.parse("PLMN 228-01\nDownload 120 Mbps").operatorName)
        assertEquals("Rogers", SpeedtestOcrParser.parse("MCC/MNC 302720\nDownload 80 Mbps").operatorName)
        assertEquals("Proximus", SpeedtestOcrParser.parse("206 01\n4G+").operatorName)
    }

    @Test
    fun parseBlankTextReturnsAllNulls() {
        val parsed = SpeedtestOcrParser.parse("   \n  ")
        assertNull(parsed.downloadMbps)
        assertNull(parsed.uploadMbps)
        assertNull(parsed.pingMs)
        assertNull(parsed.operatorName)
        assertNull(parsed.networkTech)
    }
}
