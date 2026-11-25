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

        // Régression : "Gigue au repos" ne doit pas matcher "au" -> KDDI, et "ANCRE 4G" + n78 doit donner "5G NSA"
        val splitGigueAndAnchor = SpeedtestOcrParser.parse(
            """
            n78+B1+B3+B7
            ↓ DESCENDANT
            120 Mb/s
            ↑ MONTANT
            0,6 Mb/s
            ANCRE 4G eNB 55562
            Sous charge ↓ 161 ms · gigue 83 ms
            Gigue au repos
            65 ms
            """.trimIndent()
        )
        assertNull(splitGigueAndAnchor.operatorName)
        assertEquals("5G NSA", splitGigueAndAnchor.networkTech)
        assertEquals(65.0, splitGigueAndAnchor.jitterMs ?: -1.0, 0.01)

        // Espaces autour de la virgule décimale (ex: "0, 6 Mb/s", "86, 7 Mbps")
        val spacedDecimals = SpeedtestOcrParser.parse("↓ DESCENDANT\n86, 7 Mbps\n↑ MONTANT\n0, 6 Mb/s")
        assertEquals(86.7, spacedDecimals.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.6, spacedDecimals.uploadMbps ?: -1.0, 0.01)

        // Texte OCR réel collé depuis l'appareil (Orange5G, n78+Bl+B3+B7, Gigue au repos séparée de 65 ms)
        val realDeviceOcr = """
            signal quest
            | Orange5G NSA-n78+Bl+B3+B7
            DESCENDANT
            120
            ↑ MONTANT
            0,6
            Mb/s
            Samsung SM-A5460 Le Mans
            Mb/s
            LATENCE MIN.
            41
            ms
            Moyenne 104 ms
            DESCENDANT
            ↑ MONTANT
            ANCRE 4G
            RSRP
            -111 dBm
            Sous charge
            Sous charge ↑
            Gigue au repos
            Serveur
            Speedtest- 24 novembre 2026 à 14:20
            SS-RSRP
            -115 dBm
            SS-SINR
            MAX 139 Mb/s
            8 JB
            MAX 2,6 Mb/s
            eNB 55562 • PCI 425
            10s
            161 ms- gigue 83 ms
            554 ms gigue 224 ms
            65 ms
            Poitiers- hébergé par Bouygues
            signalquest.fr
        """.trimIndent()
        val parsedReal = SpeedtestOcrParser.parse(realDeviceOcr)
        assertEquals(120.0, parsedReal.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.6, parsedReal.uploadMbps ?: -1.0, 0.01)
        assertEquals(41.0, parsedReal.pingMs ?: -1.0, 0.01)
        assertEquals(65.0, parsedReal.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsedReal.operatorName)
        assertEquals("5G NSA", parsedReal.networkTech)
        assertEquals("n78+B1+B3+B7", parsedReal.band)
        assertEquals("55562", parsedReal.cellIdentifier)

        // Texte OCR réel Cellular FR ("20 ms" au-dessus de "PING", "↑ UPLOAD" puis "150" séparé de "Mbps")
        val cellularFrRealOcr = """
            12:37 Tellement
            ll MOBILE
            Orange France
            20 ms
            PING
            DOWNLOAD
            1219
            Mbps
            DÉTAILS TECHNIQUES
            Tech: 5G NSA
            eNB: 55707
            Bandes: B7
            Accueil
            RSRP: -71 dBm
            SNR: 24 dB
            Speedtest
            & Refaire
            Speedtest
            ↑ UPLOAD
            150
            Carte
            Cloudflare
            SERVEUR
            Mbps
            < Partager
            Logs
            l 60
            Paramètres
        """.trimIndent()
        val parsedCellular = SpeedtestOcrParser.parse(cellularFrRealOcr)
        assertEquals(1219.0, parsedCellular.downloadMbps ?: -1.0, 0.01)
        assertEquals(150.0, parsedCellular.uploadMbps ?: -1.0, 0.01)
        assertEquals(20.0, parsedCellular.pingMs ?: -1.0, 0.01)
        assertEquals("Orange", parsedCellular.operatorName)
        assertEquals("5G NSA", parsedCellular.networkTech)
        assertEquals("B7", parsedCellular.band)
        assertEquals("55707", parsedCellular.cellIdentifier)

        // Carte de partage nPerf en Gb/s ("1.61" séparé de "Gb/s", "5G: NSA", "Jittee:" / "15 00 ms")
        val nperfGbpsShareCard = """
            Speed test
            #3623476400412662
            Coulaines
            Pays de la Loire, FR
            Download
            1.61
            Average:
            Packet loss :
            Gb/s
            1.35 Gb/s
            0.04 %
            Orange Mobile (FR) D
            5G: NSA
            Djperr
            Upload
            LO
            184.52
            Average
            Samsung Galaxy A54
            Android 16
            Mb/s
            2026-01-24 17:26:26 UTC
            nPerf Android 2.15.25
            176.08 Mb/s
            Latency
            24.00
            Jittee:
            Paris, Île-de-France |
            100 Gb/s moji
            ms
            15 00 ms
        """.trimIndent()
        val parsedGbps = SpeedtestOcrParser.parse(nperfGbpsShareCard)
        assertEquals(1610.0, parsedGbps.downloadMbps ?: -1.0, 0.1)
        assertEquals(184.52, parsedGbps.uploadMbps ?: -1.0, 0.01)
        assertEquals(24.0, parsedGbps.pingMs ?: -1.0, 0.01)
        assertEquals(15.0, parsedGbps.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsedGbps.operatorName)
        assertEquals("5G NSA", parsedGbps.networkTech)

        // Carte SignalQuest sombre ("TÉLÉCHARGEMENT", "861,6.", "DL chargée 153 ms", "Jitter 3,7 ms")
        val signalQuestDarkOcr = """
            SIGNALQUEST
            TÉLÉCHARGEMENT
            861,6.
            MAX 1020,9 Mbps
            DL chargée 153 ms jitter 109,3 ms
            Latence 22 ms
            Mbps
            UPLOAD
            83,1
            MAX 88,1 Mbps
            LA POSTE MOBILE• 5G NSA
            Mbps
            UL chargée 44 ms jitter 22,7 ms
            moy. 27 ms: Jitter 3,7 ms AWS à Paris
            Bande B3-eNB 517988
        """.trimIndent()
        val parsedDark = SpeedtestOcrParser.parse(signalQuestDarkOcr)
        assertEquals(861.6, parsedDark.downloadMbps ?: -1.0, 0.01)
        assertEquals(83.1, parsedDark.uploadMbps ?: -1.0, 0.01)
        assertEquals(22.0, parsedDark.pingMs ?: -1.0, 0.01)
        assertEquals(3.7, parsedDark.jitterMs ?: -1.0, 0.01)
        assertEquals("La Poste Mobile", parsedDark.operatorName)
        assertEquals("5G NSA", parsedDark.networkTech)
        assertEquals("B3", parsedDark.band)
        assertEquals("517988", parsedDark.cellIdentifier)

        // nPerf avec chiffres de jauge intercalés ("Latence", "70", "100", "Min: 19 ms")
        val nperfWithGaugeNumbers = """
            nPerf
            Réception
            Max: 213.50 Mb/s
            Moy: 174.76 Mb/s
            Connexion
            5G (NSA)
            Envoi
            [FR] Paris - 100 Gb/s - moji
            Max: 91.45 Mb/s
            Moy: 75.55 Mb/s
            Opérateur
            LA POSTE MOBILE
            Latence
            70
            100
            Min: 19 ms
            80
            90
            Réseau
            Bouygues Telecom
            PLMN: 208:20 Cell: 504743:1 TAC: 32536 RSRP: -88 dBm
            Moy: 25 ms / Gig: 15 ms
        """.trimIndent()
        val parsedGauge = SpeedtestOcrParser.parse(nperfWithGaugeNumbers)
        assertEquals(213.50, parsedGauge.downloadMbps ?: -1.0, 0.01)
        assertEquals(91.45, parsedGauge.uploadMbps ?: -1.0, 0.01)
        assertEquals(19.0, parsedGauge.pingMs ?: -1.0, 0.01)
        assertEquals(15.0, parsedGauge.jitterMs ?: -1.0, 0.01)
        assertEquals("La Poste Mobile", parsedGauge.operatorName)
        assertEquals("Bouygues Telecom", parsedGauge.networkName)
        assertEquals("5G NSA", parsedGauge.networkTech)
        assertEquals("504743:1", parsedGauge.cellIdentifier)

        // nPerf avec deux "Max:" sur la même ligne après "Envoi" et "20" + "100 Gb/s" sur la ligne serveur
        val nperfDualMaxOnOneLine = """
            12:57 12 mars
            STOP
            Serveur
            orange
            nPerf
            Test de débit
            Réception
            Connexion
            5G (NSA)
            20
            A9.l 46%
            Auto: [FR] Puteaux - 100 Gb/s - Orange
            Envoi
            Max: 397.75 Mb/s Max: 37.90 Mb/s
            Moy: 282.92 Mb/s
            Moy: 34.59 Mb/s
            Opérateur
            Orange Mobile
            Latence
            Min: 18 ms
            Réseau
            Orange
            Moy: 24 ms / Gig: 13 ms
        """.trimIndent()
        val parsedDual = SpeedtestOcrParser.parse(nperfDualMaxOnOneLine)
        assertEquals(397.75, parsedDual.downloadMbps ?: -1.0, 0.01)
        assertEquals(37.90, parsedDual.uploadMbps ?: -1.0, 0.01)
        assertEquals(18.0, parsedDual.pingMs ?: -1.0, 0.01)
        assertEquals(13.0, parsedDual.jitterMs ?: -1.0, 0.01)
        assertEquals("Orange", parsedDual.operatorName)
        assertEquals("Orange", parsedDual.networkName)
        assertEquals("5G NSA", parsedDual.networkTech)

        // nPerf avec "30" + ligne serveur "1 Gb/s" entre "Envoi" et "Max: 103.70 Mb/s", et "SFR" au-dessus de "Réseau"
        val nperfWithServerBetweenEnvoiAndMax = """
            17:34 mar. 24 déc.
            nPerf
            Test de débit
            Réception
            Max: 709.04 Mb/s
            Moy: 625.97 Mb/s
            Connexion
            5G (NSA)
            20
            PARTAGER
            RELANCER
            Envoi
            30
            Auto: [FR] Lucé - 1 Gb/s - CCIN
            Max: 103.70 Mb/s
            Moy: 92.30 Mb/s
            Opérateur
            Lebara
            40
            50
            60
            Latence
            Min: 22 ms
            SFR
            100
            Réseau
            70
            80
            90
            Moy: 25 ms / Gig: 8 ms
        """.trimIndent()
        val parsedSplit = SpeedtestOcrParser.parse(nperfWithServerBetweenEnvoiAndMax)
        assertEquals(709.04, parsedSplit.downloadMbps ?: -1.0, 0.01)
        assertEquals(103.70, parsedSplit.uploadMbps ?: -1.0, 0.01)
        assertEquals(22.0, parsedSplit.pingMs ?: -1.0, 0.01)
        assertEquals(8.0, parsedSplit.jitterMs ?: -1.0, 0.01)
        assertEquals("Lebara", parsedSplit.operatorName)
        assertEquals("SFR", parsedSplit.networkName)
        assertEquals("5G NSA", parsedSplit.networkTech)
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
            Serveur
            free
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
        assertEquals("SFR", parsed.networkName)
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
        assertEquals("La Poste Mobile", parsed.operatorName)
        assertEquals("Bouygues Telecom", parsed.networkName)
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

    @Test
    fun parseSpatialNperfBoxesWithScrambledReadingOrder() {
        val boxes = listOf(
            // Barre d'état en haut (bruit)
            OcrBoxLine("12:57", 0.05f, 0.01f, 0.15f, 0.025f),
            OcrBoxLine("46%", 0.85f, 0.01f, 0.95f, 0.025f),
            // En-tête et jauge au-dessus des colonnes
            OcrBoxLine("nPerf", 0.40f, 0.04f, 0.60f, 0.07f),
            OcrBoxLine("20", 0.10f, 0.25f, 0.18f, 0.28f),
            OcrBoxLine("100", 0.80f, 0.35f, 0.90f, 0.38f),
            OcrBoxLine("Auto: [FR] Lucé - 1 Gb/s - CCIN", 0.15f, 0.50f, 0.85f, 0.53f),
            // 3 colonnes en ordre de lecture mélangé
            OcrBoxLine("Réception", 0.08f, 0.58f, 0.28f, 0.61f),
            OcrBoxLine("Max: 709.04 Mb/s", 0.05f, 0.62f, 0.32f, 0.65f),
            OcrBoxLine("Envoi", 0.42f, 0.58f, 0.58f, 0.61f),
            OcrBoxLine("Max: 103.70 Mb/s", 0.38f, 0.62f, 0.65f, 0.65f),
            OcrBoxLine("Latence", 0.72f, 0.58f, 0.92f, 0.61f),
            OcrBoxLine("Min: 22 ms", 0.72f, 0.62f, 0.92f, 0.65f),
            OcrBoxLine("Moy: 25 ms / Gig: 8 ms", 0.68f, 0.66f, 0.95f, 0.69f),
            // Ligne Connexion / Opérateur / Réseau dans le désordre
            OcrBoxLine("SFR", 0.75f, 0.82f, 0.90f, 0.85f),
            OcrBoxLine("Réseau", 0.75f, 0.77f, 0.90f, 0.80f),
            OcrBoxLine("Opérateur", 0.40f, 0.77f, 0.60f, 0.80f),
            OcrBoxLine("Lebara", 0.40f, 0.82f, 0.60f, 0.85f),
            OcrBoxLine("Connexion", 0.08f, 0.77f, 0.28f, 0.80f),
            OcrBoxLine("5G (NSA)", 0.08f, 0.82f, 0.28f, 0.85f)
        )

        val rawText = boxes.joinToString("\n") { it.text }
        val parsed = SpeedtestOcrParser.parse(rawText, boxes)
        assertEquals(709.04, parsed.downloadMbps ?: -1.0, 0.01)
        assertEquals(103.70, parsed.uploadMbps ?: -1.0, 0.01)
        assertEquals(22.0, parsed.pingMs ?: -1.0, 0.01)
        assertEquals(8.0, parsed.jitterMs ?: -1.0, 0.01)
        assertEquals("Lebara", parsed.operatorName)
        assertEquals("SFR", parsed.networkName)
        assertEquals("5G NSA", parsed.networkTech)
    }

    @Test
    fun parseNperfWebAndCellularFrUppercaseNrBand() {
        val nperfWeb = """
            nPerf
            [FR] Anycast-FR - 100 Gb/s - Free
            35.69 Mb/s
            Moyenne 33.33 Mb/s
            30.54 Mb/s
            Moyenne 28.56 Mb/s
            14.30 ms
            Moyenne 24.04 ms
            Opérateur :
            Free
        """.trimIndent()
        val parsedWeb = SpeedtestOcrParser.parse(nperfWeb)
        assertEquals(35.69, parsedWeb.downloadMbps ?: -1.0, 0.01)
        assertEquals(30.54, parsedWeb.uploadMbps ?: -1.0, 0.01)
        assertEquals(14.30, parsedWeb.pingMs ?: -1.0, 0.01)
        assertEquals("Free", parsedWeb.operatorName)

        val cellularN77 = """
            Speedtest
            Orange France
            DOWNLOAD
            904
            Mbps
            UPLOAD
            77
            Mbps
            19 ms
            PING
            Tech: 5G NSA
            eNB: 55562
            Bandes: B3 + N77
        """.trimIndent()
        val parsedCellular = SpeedtestOcrParser.parse(cellularN77)
        assertEquals(904.0, parsedCellular.downloadMbps ?: -1.0, 0.01)
        assertEquals(77.0, parsedCellular.uploadMbps ?: -1.0, 0.01)
        assertEquals(19.0, parsedCellular.pingMs ?: -1.0, 0.01)
        assertEquals("B3+n77", parsedCellular.band)
        assertEquals("55562", parsedCellular.cellIdentifier)
    }

    @Test
    fun parseSpatialNperfShareCardAndOoklaBoxes() {
        val nperfShareBoxes = listOf(
            OcrBoxLine("nPerf Android 2.15.25", 0.70f, 0.04f, 0.95f, 0.08f),
            OcrBoxLine("Download", 0.05f, 0.36f, 0.20f, 0.42f),
            OcrBoxLine("1.61", 0.05f, 0.46f, 0.16f, 0.55f),
            OcrBoxLine("Gb/s", 0.17f, 0.49f, 0.24f, 0.54f),
            OcrBoxLine("Average: 1.35 Gb/s", 0.05f, 0.62f, 0.28f, 0.66f),
            OcrBoxLine("Upload", 0.38f, 0.36f, 0.52f, 0.42f),
            OcrBoxLine("184.52", 0.38f, 0.46f, 0.52f, 0.55f),
            OcrBoxLine("Mb/s", 0.53f, 0.49f, 0.60f, 0.54f),
            OcrBoxLine("Average: 176.08 Mb/s", 0.38f, 0.62f, 0.62f, 0.66f),
            OcrBoxLine("Latency", 0.70f, 0.36f, 0.85f, 0.42f),
            OcrBoxLine("24.00 ms", 0.70f, 0.46f, 0.85f, 0.55f),
            OcrBoxLine("Jitter: 15.00 ms", 0.70f, 0.65f, 0.92f, 0.70f)
        )
        val parsedNperfShare = SpeedtestOcrParser.parse(nperfShareBoxes.joinToString("\n") { it.text }, nperfShareBoxes)
        assertEquals(1610.0, parsedNperfShare.downloadMbps ?: -1.0, 0.1)
        assertEquals(184.52, parsedNperfShare.uploadMbps ?: -1.0, 0.01)
        assertEquals(24.0, parsedNperfShare.pingMs ?: -1.0, 0.01)
        assertEquals(15.0, parsedNperfShare.jitterMs ?: -1.0, 0.01)

        val ooklaBoxes = listOf(
            OcrBoxLine("SPEEDTEST", 0.38f, 0.05f, 0.62f, 0.08f),
            OcrBoxLine("DESCENDANT Mbps", 0.15f, 0.09f, 0.45f, 0.12f),
            OcrBoxLine("ASCENDANT Mbps", 0.55f, 0.09f, 0.85f, 0.12f),
            OcrBoxLine("1,39", 0.18f, 0.13f, 0.35f, 0.18f),
            OcrBoxLine("0,09", 0.58f, 0.13f, 0.75f, 0.18f),
            OcrBoxLine("Ping ms 381 Gigue ms 386", 0.10f, 0.20f, 0.90f, 0.23f)
        )
        val parsedOokla = SpeedtestOcrParser.parse(ooklaBoxes.joinToString("\n") { it.text }, ooklaBoxes)
        assertEquals(1.39, parsedOokla.downloadMbps ?: -1.0, 0.01)
        assertEquals(0.09, parsedOokla.uploadMbps ?: -1.0, 0.01)
        assertEquals(381.0, parsedOokla.pingMs ?: -1.0, 0.01)
        assertEquals(386.0, parsedOokla.jitterMs ?: -1.0, 0.01)
    }
}
