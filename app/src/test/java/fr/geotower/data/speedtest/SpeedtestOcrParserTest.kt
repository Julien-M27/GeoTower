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
